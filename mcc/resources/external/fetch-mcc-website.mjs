/*
 * Downloads the MCC public website (https://github.com/bimberlabinternal/mcc-website) and converts it into
 * module HTML views, so that LabKey can inject a CSP nonce into each <script> tag via <%=scriptNonce%>.
 *
 * Output:
 *   ./resources/web/gen/mcc-website-assets/
 *   ./resources/views/gen/<view>.html        <- one view per root-level HTML page
 *   ./resources/views/gen/<view>.view.xml    <- generated from ./resources/external/template.view.xml
 *
 * Pages are served as <contextPath><containerPath>/mcc-<view>.view, e.g. /home/mcc-index.view
 *
 */
import degit from 'degit';
import { existsSync } from 'node:fs';
import { copyFile, mkdir, mkdtemp, readdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join, relative, resolve, sep } from 'node:path';

const REPO = 'bimberlabinternal/mcc-website';
const REF = process.env.MCC_WEBSITE_REF || 'master';
const CONTROLLER = 'mcc';

const ROOT = resolve('.');
const VIEW_TEMPLATE = join(ROOT, 'resources', 'external', 'template.view.xml');
const VIEWS_DIR = join(ROOT, 'resources', 'views');
const VIEW_GEN_DIR = join(VIEWS_DIR, 'gen');
const WEB_GEN_DIR = join(ROOT, 'resources', 'web', 'gen');

const ASSETS_DIR_NAME = 'mcc-website-assets';
const ASSETS_DIR = join(WEB_GEN_DIR, ASSETS_DIR_NAME);
const ASSETS_URL = `<%=contextPath%>/gen/${ASSETS_DIR_NAME}/`;
const MANIFEST = join(VIEW_GEN_DIR, '.mcc-website-manifest.json');

const IGNORED_FILES = new Set(['.DS_Store']);
const SERVER_URL = /https?:\/\/mcc\.ohsu\.edu\/?/gi;

async function main() {
    const tmp = await mkdtemp(join(tmpdir(), 'mcc-website-'));
    try {
        // Step 1: download the entire repo as a tarball
        await download(tmp);

        // Step 2: remove output from any previous run (only files generated with this .mjs script, tracked with a manifest)
        await cleanPreviousOutput();

        // Step 3: copy assets and fix relative url() references in the CSS
        const assetCount = await copyDir(join(tmp, 'assets'), ASSETS_DIR);
        await rewriteCss(ASSETS_DIR);

        // Step 4: convert each root-level HTML page into a module view
        const pages = (await readdir(tmp, { withFileTypes: true }))
            .filter(d => d.isFile() && d.name.toLowerCase().endsWith('.html'))
            .map(d => d.name)
            .sort();

        if (!pages.length) {
            throw new Error(`No HTML files found in ${REPO}@${REF}`);
        }

        const viewNames = new Map(); // lowercase filename is used for the view name
        for (const page of pages) {
            viewNames.set(page.toLowerCase(), toViewName(page));
        }
        await checkForCollisions([...viewNames.values()]);

        await mkdir(VIEW_GEN_DIR, { recursive: true });
        const viewTemplate = await readFile(VIEW_TEMPLATE, 'utf8');
        const generated = [];
        for (const page of pages) {
            const viewName = viewNames.get(page.toLowerCase());
            const html = await readFile(join(tmp, page), 'utf8');

            await writeFile(join(VIEW_GEN_DIR, viewName + '.html'), transformHtml(html, page, viewNames));
            await writeFile(join(VIEW_GEN_DIR, viewName + '.view.xml'), makeViewXml(viewTemplate, html));
            generated.push(viewName + '.html', viewName + '.view.xml');

            console.log(`  ${page} -> ${CONTROLLER}-${viewName}.view`);
        }

        await writeFile(MANIFEST, JSON.stringify({ repo: REPO, ref: REF, files: generated }, null, 2) + '\n');
        console.log(`MCC website: generated ${pages.length} views and copied ${assetCount} assets from ${REPO}@${REF}`);
    } finally {
        await rm(tmp, { recursive: true, force: true });
    }
}

async function download(destDir) {
    console.log(`Downloading ${REPO}#${REF}`);
    await degit(`${REPO}#${REF}`, { cache: false }).clone(destDir);

    if (!existsSync(join(destDir, 'assets'))) {
        throw new Error(`Downloaded archive does not contain an 'assets' directory`);
    }
}

async function cleanPreviousOutput() {
    await rm(ASSETS_DIR, { recursive: true, force: true });

    if (existsSync(MANIFEST)) {
        const manifest = JSON.parse(await readFile(MANIFEST, 'utf8'));
        for (const f of manifest.files ?? []) {
            await rm(join(VIEW_GEN_DIR, basename(f)), { force: true });
        }
        await rm(MANIFEST, { force: true });
    }
}

async function copyDir(src, dest) {
    let count = 0;
    await mkdir(dest, { recursive: true });
    for (const entry of await readdir(src, { withFileTypes: true })) {
        if (IGNORED_FILES.has(entry.name)) {
            continue;
        }

        const from = join(src, entry.name);
        const to = join(dest, entry.name);
        if (entry.isDirectory()) {
            count += await copyDir(from, to);
        } else if (entry.isFile()) {
            await copyFile(from, to);
            count++;
        }
    }

    return count;
}

// CSS in the site references images as url("../../assets/img/x.jpg"), which assumes 'assets' sits at the web root.
// Rewrite these relative to the new location of the assets dir.
async function rewriteCss(dir) {
    for (const entry of await readdir(dir, { withFileTypes: true, recursive: true })) {
        if (!entry.isFile() || !entry.name.endsWith('.css')) {
            continue;
        }

        const file = join(entry.parentPath ?? entry.path, entry.name);
        let toRoot = relative(dirname(file), ASSETS_DIR).split(sep).join('/');
        toRoot = toRoot ? toRoot + '/' : './';

        const css = await readFile(file, 'utf8');
        const updated = css.replace(/url\(\s*(["']?)(?:\.\.\/)+assets\//g, `url($1${toRoot}`);
        if (updated !== css) {
            await writeFile(file, updated);
        }
    }
}

// LabKey parses <controller>-<action>.view by splitting on the last hyphen, so view names cannot contain hyphens.
// 'become-a-user.html' -> 'becomeAUser', 'LEARN.html' -> 'learn'
function toViewName(fileName) {
    const parts = fileName.replace(/\.html$/i, '').toLowerCase().split(/[^a-z0-9]+/).filter(Boolean);
    if (!parts.length) {
        throw new Error(`Cannot derive a view name from: ${fileName}`);
    }

    return parts[0] + parts.slice(1).map(p => p.charAt(0).toUpperCase() + p.slice(1)).join('');
}

// A generated page should not shadow a view that already exists
async function checkForCollisions(names) {
    const existing = new Set();
    for (const dir of [VIEWS_DIR, VIEW_GEN_DIR]) {
        if (!existsSync(dir)) {
            continue;
        }

        for (const entry of await readdir(dir, { withFileTypes: true })) {
            const m = entry.isFile() && entry.name.match(/^(.+?)\.(html|view\.xml)$/);
            if (m) {
                existing.add(m[1].toLowerCase());
            }
        }
    }

    const collisions = names.filter(n => existing.has(n.toLowerCase()));
    if (collisions.length) {
        throw new Error(`MCC website page(s) collide with existing module views: ${collisions.join(', ')}`);
    }
}

function transformHtml(html, fileName, viewNames) {
    // Using the original HTML, check for unresolved CSP violations and throw a warning
    warnOnCspViolations(html, fileName);

    let ret = html;

    // Remove DOCTYPE, since this is not an embedded page 
    ret = ret.replace(/^\s*<!DOCTYPE[^>]*>\s*/i, '');

    // Absolute links to the live server: href="https://mcc.ohsu.edu/login-login.view" replaced with href="<%=contextPath%>/login-login.view"
    ret = ret.replace(SERVER_URL, '<%=contextPath%>/');

    // Asset paths: href="assets/..", src="/assets/..", style="background: url(&quot;assets/..&quot;)"
    ret = ret.replace(/(["'(]|&quot;)\/?assets\//g, `$1${ASSETS_URL}`);

    // Links between pages: href="index.html#mission" replaced with href="<%=contextPath%><%=containerPath%>/mcc-index.view#mission"
    ret = ret.replace(/href=(["'])([^"'#?:/]+\.html)([#?][^"']*)?\1/gi, (match, quote, target, suffix = '') => {
        const viewName = viewNames.get(target.toLowerCase());
        if (!viewName) {
            console.warn(`WARNING: ${fileName} links to unknown page: ${target}`);
            return match;
        }

        return `href=${quote}<%=contextPath%><%=containerPath%>/${CONTROLLER}-${viewName}.view${suffix}${quote}`;
    });

    // Inject per-request nonce
    ret = ret.replace(/<script\b(?![^>]*\bnonce=)/gi, '<script nonce="<%=scriptNonce%>"');

    return ret;
}

function warnOnCspViolations(html, fileName) {
    const handlers = html.match(/<[^>]+\son[a-z]+\s*=/gi);
    if (handlers) {
        console.warn(`WARNING: ${fileName} contains ${handlers.length} inline event handler(s) (e.g. onclick=), which CSP may block`);
    }

    if (/javascript:/i.test(html)) {
        console.warn(`WARNING: ${fileName} contains a javascript: URL, which CSP may block`);
    }

    const inlineScripts = [...html.matchAll(/<script\b([^>]*)>([\s\S]*?)<\/script>/gi)]
        .filter(m => !/\bsrc\s*=/i.test(m[1]) && m[2].trim());
    if (inlineScripts.length) {
        console.warn(`WARNING: ${fileName} contains ${inlineScripts.length} inline script(s), which CSP may block`);
    }
}

function makeViewXml(template, html) {
    const title = html.match(/<title[^>]*>([\s\S]*?)<\/title>/i)?.[1]?.trim();
    if (!title) {
        return template;
    }

    return template.replace(/(<view\b[^>]*\btitle=")[^"]*(")/, `$1${title.replace(/"/g, '&quot;')}$2`);
}

main().catch(err => {
    console.error(err);
    process.exit(1);
});
