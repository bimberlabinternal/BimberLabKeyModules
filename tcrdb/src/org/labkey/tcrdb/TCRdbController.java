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

import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.IntHashMap;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.sequenceanalysis.RefNtSequenceModel;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringUtilsLabKey;
import org.springframework.validation.BindException;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TCRdbController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(TCRdbController.class);
    public static final String NAME = "tcrdb";

    private static final Logger _log = LogManager.getLogger(TCRdbController.class);

    public TCRdbController()
    {
        setActionResolver(_actionResolver);
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

    @RequiresPermission(ReadPermission.class)
    @IgnoresTermsOfUse
    public static class DownloadSequenceAction extends ExportAction<DownloadCloneMaterialsForm>
    {
        @Override
        public void export(DownloadCloneMaterialsForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            Container target = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
            UserSchema us = QueryService.get().getUserSchema(getUser(), target, form.getSchemaName());
            if (us == null)
            {
                errors.reject(ERROR_MSG, "Unable to find schema: " + form.getSchemaName());
                return;
            }

            TableInfo assayData = us.getTable(form.getQueryName());
            if (assayData == null)
            {
                errors.reject(ERROR_MSG, "Unable to find table: " + form.getQueryName());
                return;
            }

            List<String> rowIds = Arrays.asList(form.getRowId());
            if (rowIds.isEmpty())
            {
                errors.reject(ERROR_MSG, "No rows provided");
                return;
            }

            StringBuilder fasta = new StringBuilder();
            Map<Integer, Set<String>> segmentsByLibrary = new IntHashMap<>();

            //find assay records
            SimpleFilter assayFilter = new SimpleFilter(FieldKey.fromString("rowId"), rowIds, CompareType.IN);
            Map<FieldKey, ColumnInfo> cols = QueryService.get().getColumns(assayData, PageFlowUtil.set(
                    FieldKey.fromString("samplename"),
                    FieldKey.fromString("sequence"),
                    FieldKey.fromString("cdr3"),
                    FieldKey.fromString("vHit"),
                    FieldKey.fromString("jHit"),
                    FieldKey.fromString("dHit"),
                    FieldKey.fromString("cHit"),
                    FieldKey.fromString("libraryId/libraryId"),
                    FieldKey.fromString("analysisId/library_id")));
            TableSelector ts = new TableSelector(assayData, cols.values(), assayFilter, null);
            Set<String> segmentsByName = new HashSet<>();
            final String[] segmentFields = new String[]{"vHit", "jHit", "cHit"};
            ts.forEachResults(rs -> {
                // Allows this to work with both MiXCR and 10x data
                Integer libraryId = null;
                if (rs.getObject(FieldKey.fromString("libraryId/libraryId")) != null)
                {
                    libraryId = rs.getInt(FieldKey.fromString("libraryId/libraryId"));
                }

                if (libraryId == null && rs.getObject(FieldKey.fromString("analysisId/library_id")) != null)
                {
                    libraryId = rs.getInt(FieldKey.fromString("analysisId/library_id"));
                }

                for (String fn : segmentFields)
                {
                    if (rs.getString(FieldKey.fromString(fn)) != null)
                    {
                        if (libraryId != null)
                        {
                            Set<String> map = segmentsByLibrary.getOrDefault(libraryId, new HashSet<>());
                            map.add(StringUtils.trimToNull(rs.getString(FieldKey.fromString(fn))));
                            segmentsByLibrary.put(libraryId, map);
                        }
                        else
                        {
                            segmentsByName.add(StringUtils.trimToNull(rs.getString(FieldKey.fromString(fn))));
                        }
                    }
                }

                fasta.append(">").append(rs.getString("samplename")).append("_").append(rs.getString("cdr3")).append("\n");
                if (rs.getObject(FieldKey.fromString("sequence")) != null)
                {
                    fasta.append(rs.getString("sequence")).append("\n");
                }
                else
                {
                    fasta.append("No Data").append("\n");
                }
            });

            // look up segments in NT table
            Set<String> missingSegments = new HashSet<>(segmentsByName);
            for (int libraryId : segmentsByLibrary.keySet())
            {
                missingSegments.addAll(segmentsByLibrary.get(libraryId));
            }

            if (!segmentsByLibrary.isEmpty())
            {
                for (int libraryId : segmentsByLibrary.keySet())
                {
                    SimpleFilter ntFilter = new SimpleFilter();
                    ntFilter.addCondition(FieldKey.fromString("ref_nt_id/datedisabled"), null, CompareType.ISBLANK);
                    ntFilter.addCondition(FieldKey.fromString("library_id"), libraryId, CompareType.EQUAL);
                    ntFilter.addClause(new SimpleFilter.OrClause(
                            new SimpleFilter.InClause(FieldKey.fromString("ref_nt_id/name"), segmentsByLibrary.get(libraryId)),
                            new SimpleFilter.InClause(FieldKey.fromString("ref_nt_id/lineage"), segmentsByLibrary.get(libraryId))
                    ));
                    new TableSelector(QueryService.get().getUserSchema(getUser(), target, "sequenceanalysis").getTable("reference_library_members"), PageFlowUtil.set("ref_nt_id"), ntFilter, null).forEachResults(rs -> {
                        RefNtSequenceModel nt = RefNtSequenceModel.getForRowId(rs.getInt(FieldKey.fromString("ref_nt_id")));
                        fasta.append(">").append(nt.getName() + (nt.getSpecies() != null ? "-" + nt.getSpecies() : "")).append('\n').append(nt.getSequence()).append('\n');
                        missingSegments.remove(nt.getName());
                        missingSegments.remove(nt.getLineage());
                    });
                }
            }

            if (!segmentsByName.isEmpty())
            {
                SimpleFilter ntFilter = new SimpleFilter(FieldKey.fromString("name"), segmentsByName, CompareType.IN);
                ntFilter.addCondition(FieldKey.fromString("datedisabled"), null, CompareType.ISBLANK);

                new TableSelector(QueryService.get().getUserSchema(getUser(), target, "sequenceanalysis").getTable("ref_nt_sequences"), PageFlowUtil.set("rowid"), ntFilter, null).forEachResults(rs -> {
                    RefNtSequenceModel nt = RefNtSequenceModel.getForRowId(rs.getInt(FieldKey.fromString("rowid")));
                    fasta.append(">").append(nt.getName() + (nt.getSpecies() != null ? "-" + nt.getSpecies() : "")).append('\n').append(nt.getSequence()).append('\n');
                    missingSegments.remove(nt.getName());
                });
            }

            if (!missingSegments.isEmpty())
            {
                logger.error("Unable to find the following NT sequences: [" + StringUtils.join(missingSegments, "],[") + "]");
            }

            PageFlowUtil.prepareResponseForFile(response, Collections.emptyMap(), "TCR_Data.fasta", true);
            if (fasta.isEmpty())
            {
                response.getOutputStream().write("No data found".getBytes(StringUtilsLabKey.DEFAULT_CHARSET));
            }
            else
            {
                response.getOutputStream().write(fasta.toString().getBytes(StringUtilsLabKey.DEFAULT_CHARSET));
            }
        }
    }

    public static class DownloadCloneMaterialsForm
    {
        private String[] _rowId;
        private String _schemaName;
        private String _queryName;

        public String[] getRowId()
        {
            return _rowId;
        }

        public void setRowId(String[] rowId)
        {
            _rowId = rowId;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }
    }
}
