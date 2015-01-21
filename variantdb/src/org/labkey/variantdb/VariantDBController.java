/*
 * Copyright (c) 2014 LabKey Corporation
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

package org.labkey.variantdb;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.sequenceanalysis.SequenceOutputFile;
import org.labkey.api.view.NotFoundException;
import org.labkey.variantdb.pipeline.DbSnpImportPipelineJob;
import org.labkey.variantdb.pipeline.DbSnpImportPipelineProvider;
import org.labkey.variantdb.pipeline.VariantImportPipelineJob;
import org.springframework.validation.BindException;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class VariantDBController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(VariantDBController.class);
    public static final String NAME = "variantdb";
    private static final Logger _log = Logger.getLogger(VariantDBController.class);

    public VariantDBController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class LoadDbSnpDataAction extends ApiAction<LoadDbSnpForm>
    {
        public ApiResponse execute(LoadDbSnpForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            if (StringUtils.trimToNull(form.getSnpPath()) == null)
            {
                errors.reject(ERROR_MSG, "Must provide the name of the remote directory");
                return null;
            }

            if (form.getGenomeId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the Id of the base genome to use");
                return null;
            }

            URL url = new URL(DbSnpImportPipelineProvider.URL_BASE + "/" + form.getSnpPath() + "/") ;
            try (InputStream inputStream = url.openConnection().getInputStream())
            {
                //just open to test if file exists
            }
            catch (IOException e)
            {
                throw new NotFoundException("Unable to find remote file: " + form.getSnpPath());
            }

            URL url2 = new URL(DbSnpImportPipelineProvider.URL_BASE + "/" + form.getSnpPath() + "/VCF/") ;
            try (InputStream inputStream = url2.openConnection().getInputStream())
            {
                //just open to test if file exists
            }
            catch (IOException e)
            {
                throw new NotFoundException("FTP location does not have a subdirectory named VCF");
            }

            DbSnpImportPipelineJob job = new DbSnpImportPipelineJob(getContainer(), getUser(), getViewContext().getActionURL(), pr, form.getSnpPath(), form.getGenomeId());
            PipelineService.get().queueJob(job);

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class LoadDbSnpForm
    {
        private String _snpPath;
        private Integer _genomeId;

        public String getSnpPath()
        {
            return _snpPath;
        }

        public void setSnpPath(String snpPath)
        {
            _snpPath = snpPath;
        }

        public Integer getGenomeId()
        {
            return _genomeId;
        }

        public void setGenomeId(Integer genomeId)
        {
            _genomeId = genomeId;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class VariantImportAction extends ApiAction<VariantImportForm>
    {
        public ApiResponse execute(VariantImportForm form, BindException errors) throws Exception
        {
            PipeRoot pr = PipelineService.get().findPipelineRoot(getContainer());
            if (pr == null || !pr.isValid())
                throw new NotFoundException();

            if (form.getOutputFileIds() == null || form.getOutputFileIds().length == 0)
            {
                errors.reject(ERROR_MSG, "Must provide the output files to process");
                return null;
            }


            List<SequenceOutputFile> outputFiles = new ArrayList<>();
            for (Integer id : form.getOutputFileIds())
            {
                SequenceOutputFile o = SequenceOutputFile.getForId(id);
                if (o != null)
                {
                    outputFiles.add(o);
                }
            }

            List<Integer> liftoverTargets = null;
            if (form.getLiftOverTargetGenomes() != null)
            {
                liftoverTargets = new ArrayList<>(Arrays.asList(form.getLiftOverTargetGenomes()));
            }

            VariantImportPipelineJob job = new VariantImportPipelineJob(getContainer(), getUser(), getViewContext().getActionURL(), pr, outputFiles, liftoverTargets);
            PipelineService.get().queueJob(job);

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class VariantImportForm
    {
        private Integer[] _outputFileIds;
        private Integer[] _liftOverTargetGenomes;

        public Integer[] getOutputFileIds()
        {
            return _outputFileIds;
        }

        public void setOutputFileIds(Integer[] outputFileIds)
        {
            _outputFileIds = outputFileIds;
        }

        public Integer[] getLiftOverTargetGenomes()
        {
            return _liftOverTargetGenomes;
        }

        public void setLiftOverTargetGenomes(Integer[] liftOverTargetGenomes)
        {
            _liftOverTargetGenomes = liftOverTargetGenomes;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    @CSRF
    public class LiftOverVariantsAction extends ApiAction<LiftOverVariantsForm>
    {
        public ApiResponse execute(LiftOverVariantsForm form, BindException errors) throws Exception
        {
            if (StringUtils.trimToNull(form.getBatchId()) == null)
            {
                errors.reject(ERROR_MSG, "Must provide the batch Id");
                return null;
            }

            if (form.getSourceGenomeId() == null)
            {
                errors.reject(ERROR_MSG, "Must provide the source genome Id");
                return null;
            }

            try
            {
                VariantDBManager.get().liftOverVariants(form.getSourceGenomeId(), new SimpleFilter(FieldKey.fromString("batchId"), form.getBatchId()), _log, getUser());
            }
            catch (SQLException e)
            {
                _log.error("Error with liftover", e);

                errors.reject(ERROR_MSG, e.getMessage());
                return null;
            }

            return new ApiSimpleResponse("success", true);
        }
    }

    public static class LiftOverVariantsForm
    {
        private String _batchId;
        private Integer _sourceGenomeId;

        public String getBatchId()
        {
            return _batchId;
        }

        public void setBatchId(String batchId)
        {
            _batchId = batchId;
        }

        public Integer getSourceGenomeId()
        {
            return _sourceGenomeId;
        }

        public void setSourceGenomeId(Integer sourceGenomeId)
        {
            _sourceGenomeId = sourceGenomeId;
        }
    }
}