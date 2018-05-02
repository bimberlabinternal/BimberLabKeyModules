/*
 * Copyright (c) 2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.tcrdb;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.StopIteratingException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.pipeline.PipelineJobException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.Readers;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.SpringErrorView;
import org.labkey.tcrdb.pipeline.MiXCRWrapper;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.StringWriter;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TCRdbController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TCRdbController.class);
    public static final String NAME = "tcrdb";

    private static final Logger _log = Logger.getLogger(TCRdbController.class);

    public TCRdbController()
    {
        setActionResolver(_actionResolver);
    }

    //TODO: based on set of IDs, run exportAlignmentsPretty

    @RequiresPermission(ReadPermission.class)
    public class ExportAlignmentsAction extends SimpleViewAction<ExportAlignmentsForm>
    {
        @Override
        public ModelAndView getView(ExportAlignmentsForm form, BindException errors) throws Exception
        {
            if (form.getAssayRowIds() == null || form.getAssayRowIds().length == 0)
            {
                errors.reject(ERROR_MSG, "Must provide IDs to display");
                return new SpringErrorView(errors);
            }

            if (StringUtils.isEmpty(form.getSchemaName()))
            {
                errors.reject(ERROR_MSG, "Must provide the assay schema name");
                return new SpringErrorView(errors);
            }

            //find rows
            UserSchema us = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
            if (us == null)
            {
                errors.reject(ERROR_MSG, "Unknown schema: " + form.getSchemaName());
                return new SpringErrorView(errors);
            }

            TableInfo ti = us.getTable("data");
            final Map<File, List<AssayRecord>> VDJMap = new HashMap<>();
            List<Integer> rowIds = new ArrayList<>();
            rowIds.addAll(Arrays.asList(form.getAssayRowIds()));

            TableSelector ts = new TableSelector(ti, new SimpleFilter(FieldKey.fromString("rowid"), rowIds, CompareType.IN), null);
            final StringWriter writer = new StringWriter();

            ts.forEach(new Selector.ForEachBlock<AssayRecord>()
            {
                @Override
                public void exec(AssayRecord r) throws SQLException, StopIteratingException
                {
                    if (r.getVdjFile() == null)
                    {
                        writer.write("ERROR: Row lacks VDJCA file: " + r.getRowId() + "\n");
                        return;
                    }

                    ExpData d = ExperimentService.get().getExpData(r.getVdjFile());
                    if (d == null)
                    {
                        writer.write("ERROR: Unable to find VDJCA file for row: " + r.getRowId() + ", ExpData: " + r.getVdjFile() + "\n");
                        return;
                    }

                    if (!d.getFile().exists())
                    {
                        writer.write("ERROR: Unable to find VDJCA file for row: " + r.getRowId() + ", file does not exist: " + d.getFile().getPath() + "\n");
                        return;
                    }

                    if (!VDJMap.containsKey(d.getFile()))
                    {
                        VDJMap.put(d.getFile(), new ArrayList<>());
                    }

                    VDJMap.get(d.getFile()).add(r);
                }
            }, AssayRecord.class);

            if (VDJMap.isEmpty())
            {
                errors.reject(ERROR_MSG, "No matching rows found for IDs: " + StringUtils.join(rowIds, ","));
                return new SpringErrorView(errors);
            }

            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd");
            for (File f : VDJMap.keySet())
            {
                File tmp = File.createTempFile("mixcr", ".txt");
                MiXCRWrapper wrapper = new MiXCRWrapper(_log);
                //TODO:
                //wrapper.setLibraryPath();
                List<String> args = new ArrayList<>();
                if (StringUtils.trimToNull(form.getCdr3Equals()) != null)
                {
                    args.add("--cdr3-equals");
                    args.add(form.getCdr3Equals());
                }

                if (StringUtils.trimToNull(form.getReadContains()) != null)
                {
                    args.add("--read-contains");
                    args.add(form.getReadContains());
                }

                try
                {
                    wrapper.doExportAlignmentsPretty(f, tmp, args);

                    writer.write("File: " + f.getName() + '\n');
                    writer.write("Result Rows From This File: " + '\n');
                    for (AssayRecord r : VDJMap.get(f))
                    {
                        writer.write("Sample: " + r.getSampleName() + '\n');
                        writer.write("Sample Date: " + (r.getDate() == null ? "" : fmt.format(r.getDate())) + '\n');
                        writer.write("CDR3: " + coalesce(r.getCDR3()) + '\n');
                        writer.write("vHit: " + coalesce(r.getvHit()) + '\n');
                        writer.write("dHit: " + coalesce(r.getdHit()) + '\n');
                        writer.write("jHit: " + coalesce(r.getjHit()) + '\n');
                        writer.write("cHit: " + coalesce(r.getcHit()) + '\n');
                        writer.write("Read Count: " + coalesce(r.getCount()) + '\n');
                        writer.write("Fraction: " + coalesce(r.getFraction()) + '\n');
                        writer.write("Comments: " + coalesce(r.getComment()) + '\n');
                        writer.write('\n');
                    }
                    writer.write('\n');
                    try (BufferedReader reader = Readers.getReader(new FileInputStream(tmp)))
                    {
                        String line;
                        boolean inAlignmentBlock = false;
                        while ((line = reader.readLine()) != null)
                        {
                            line = line.replaceAll("<", "&lt;").replaceAll(">", "&gt;");

                            String trimmed = StringUtils.trimToEmpty(line);
                            if (StringUtils.isEmpty(trimmed))
                            {
                                inAlignmentBlock = false;
                            }

                            //Highlight mismatches
                            if (inAlignmentBlock)
                            {
                                String[] tokens = trimmed.split("( )+");
                                String alignmentRaw = tokens[2];
                                StringBuilder sb = new StringBuilder();
                                for (int i=0; i<alignmentRaw.length();i++)
                                {
                                    char c = alignmentRaw.charAt(i);
                                    if (Character.isUpperCase(c))
                                    {
                                        sb.append("<span style=\"background: yellow;\">");
                                        sb.append(c);
                                        sb.append("</span>");
                                    }
                                    else
                                    {
                                        sb.append(c);
                                    }
                                }

                                line = line.replaceAll(alignmentRaw, sb.toString());
                            }

                            writer.write(line + '\n');

                            if (trimmed.startsWith("Target"))
                            {
                                inAlignmentBlock = true;
                            }
                        }
                    }

                    writer.write('\n');
                    writer.write("<hr>");
                    writer.write('\n');
                    writer.write('\n');

                    tmp.delete();
                }
                catch (PipelineJobException e){
                    writer.write("Unable to run export alignments\n");

                    _log.error("Unable to run exportAlignments:\n" + StringUtils.join(wrapper.getCommandsExecuted(), "\n"), e);
                }
            }

            //mixcr exportReadsForClones index_file alignments.vdjca.gz 0 1 2 33 54 reads.fastq.gz
            //mixcr exportAlignmentsPretty input.vdjca test.txt

            return new HtmlView("MiXCR Alignments", "<div style=\"font-family:courier,Courier New,monospace;white-space:nowrap;padding:5px;\"><pre>" + writer.toString() + "</pre></div>");
        }

        private String coalesce(Object s)
        {
            return s == null ? "None" : s.toString();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("TCR Data Export"); //necessary to set page title, it seems
            return root;
        }
    }

    public static class AssayRecord
    {
        private Integer _rowId;
        private Date _date;
        private String _CDR3;
        private String _vHit;
        private String _dHit;
        private String _jHit;
        private String _cHit;
        private Integer _clonesFile;
        private String _sampleName;
        private String _cloneId;
        private Integer _alignmentId;
        private Integer _vdjFile;
        private String _comment;
        private Integer _count;
        private Double _fraction;

        public Integer getRowId()
        {
            return _rowId;
        }

        public void setRowId(Integer rowId)
        {
            _rowId = rowId;
        }

        public Integer getClonesFile()
        {
            return _clonesFile;
        }

        public void setClonesFile(Integer clonesFile)
        {
            _clonesFile = clonesFile;
        }

        public String getSampleName()
        {
            return _sampleName;
        }

        public void setSampleName(String sampleName)
        {
            _sampleName = sampleName;
        }

        public String getCloneId()
        {
            return _cloneId;
        }

        public void setCloneId(String cloneId)
        {
            _cloneId = cloneId;
        }

        public Integer getAlignmentId()
        {
            return _alignmentId;
        }

        public void setAlignmentId(Integer alignmentId)
        {
            _alignmentId = alignmentId;
        }

        public Integer getVdjFile()
        {
            return _vdjFile;
        }

        public void setVdjFile(Integer vdjFile)
        {
            _vdjFile = vdjFile;
        }

        public Date getDate()
        {
            return _date;
        }

        public void setDate(Date date)
        {
            _date = date;
        }

        public String getCDR3()
        {
            return _CDR3;
        }

        public void setCDR3(String CDR3)
        {
            _CDR3 = CDR3;
        }

        public String getvHit()
        {
            return _vHit;
        }

        public void setvHit(String vHit)
        {
            _vHit = vHit;
        }

        public String getdHit()
        {
            return _dHit;
        }

        public void setdHit(String dHit)
        {
            _dHit = dHit;
        }

        public String getjHit()
        {
            return _jHit;
        }

        public void setjHit(String jHit)
        {
            _jHit = jHit;
        }

        public String getcHit()
        {
            return _cHit;
        }

        public void setcHit(String cHit)
        {
            _cHit = cHit;
        }

        public String getComment()
        {
            return _comment;
        }

        public void setComment(String comment)
        {
            _comment = comment;
        }

        public Integer getCount()
        {
            return _count;
        }

        public void setCount(Integer count)
        {
            _count = count;
        }

        public Double getFraction()
        {
            return _fraction;
        }

        public void setFraction(Double fraction)
        {
            _fraction = fraction;
        }
    }

    public static class ExportAlignmentsForm
    {
        private Integer[] _assayRowIds;
        private String schemaName;
        private String _cdr3Equals;
        private String _readContains;

        public Integer[] getAssayRowIds()
        {
            return _assayRowIds;
        }

        public void setAssayRowIds(Integer[] assayRowIds)
        {
            _assayRowIds = assayRowIds;
        }

        public String getSchemaName()
        {
            return schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            this.schemaName = schemaName;
        }

        public String getCdr3Equals()
        {
            return _cdr3Equals;
        }

        public void setCdr3Equals(String cdr3Equals)
        {
            _cdr3Equals = cdr3Equals;
        }

        public String getReadContains()
        {
            return _readContains;
        }

        public void setReadContains(String readContains)
        {
            _readContains = readContains;
        }
    }
}
