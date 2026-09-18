import { join, resolve } from 'node:path';

const ROOT = resolve('.');
const WEB_GEN_DIR = join(ROOT, 'resources', 'web', 'gen');

const ASSETS_DIR = join(WEB_GEN_DIR, 'mcc-website-assets');
const VIEW_GEN_DIR = join(ROOT, 'resources', 'views', 'gen');

async function main() {
    // NOTE: we probably should not assume WEB_GEN_DIR or ASSETS_DIR exist. We should pre-clean existing files
    // remember 'gen' is also used by rspack so we cant just delete everything

    // Step 1: download content from github:
    // https://github.com/bimberlabinternal/mcc-website
    // consider: https://www.npmjs.com/package/degit
    // Download files within 'assets' dir to  ./resources/web/gen/mcc-website-assets
    // Download *.HTML from github -> ./resources/web/gen/

    // Step 2: need to string-substitute most href and src paths in each HTML file:
    // 'assets' -> '<%=contextPath%>mcc-website-assets' (I think)
    // Any link to an HTML file need to change from:
    // href="index.html" -> href="<%=contextPath%><%=containerPath%>mcc-index.view" (I think)

    // Step 3: we should use template.view.xml as a template and copy a version next to each HTML file. This sets guest permissions.
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
