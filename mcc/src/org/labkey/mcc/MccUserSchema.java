package org.labkey.mcc;

import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveTreeSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.ldk.table.ContainerScopedTable;
import org.labkey.api.ldk.table.CustomPermissionsTable;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.mcc.security.MccDataAdminPermission;
import org.labkey.mcc.security.MccRabReviewPermission;
import org.labkey.mcc.security.MccRequestAdminPermission;
import org.labkey.mcc.security.MccRequestorPermission;
import org.labkey.mcc.security.MccViewRequestsPermission;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class MccUserSchema extends SimpleUserSchema
{
    private static final Logger _log = LogHelper.getLogger(MccUserSchema.class, "MCC UserSchema messages");

    public MccUserSchema(User user, Container container, DbSchema dbschema)
    {
        super(MccSchema.NAME, "MCC-specific tables, such as requests", user, container, dbschema);
    }

    @Override
    public TableInfo createTable(String name, ContainerFilter cf)
    {
        if (supportsAggregatedTables())
        {
            if (TABLE_AGGREGATED_WEIGHT.equalsIgnoreCase(name))
            {
                return getWeightQuery();
            }
            else if (TABLE_AGGREGATED_KINSHIP.equalsIgnoreCase(name))
            {
                return getKinshipQuery();
            }
            else if (TABLE_AGGREGATED_DEPARTURES.equalsIgnoreCase(name))
            {
                return getDepartureQuery();
            }
            else if (TABLE_AGGREGATED_GENOMICS.equalsIgnoreCase(name))
            {
                return getGenomicsQuery();
            }
            else if (TABLE_AGGREGATED_OBS.equalsIgnoreCase(name))
            {
                return getObsQuery();
            }
            else if (TABLE_AGGREGATED_CENSUS.equalsIgnoreCase(name))
            {
                return getCensusQuery();
            }
        }

        return super.createTable(name, cf);
    }

    @Override
    @Nullable
    protected TableInfo createWrappedTable(String name, @NotNull TableInfo schemaTable, ContainerFilter cf)
    {
        if (MccSchema.TABLE_ANIMAL_MAPPING.equalsIgnoreCase(name))
        {
            return new ContainerScopedTable<>(this, schemaTable, cf, "subjectname").init();
        }
        else if (MccSchema.TABLE_ANIMAL_REQUESTS.equalsIgnoreCase(name))
        {
            CustomPermissionsTable<?> ret = new CustomPermissionsTable<>(this, schemaTable, cf);
            ret.addPermissionMapping(InsertPermission.class, MccRequestorPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, MccRequestorPermission.class);
            ret.addPermissionMapping(DeletePermission.class, MccRequestAdminPermission.class);

            return ret.init();
        }
        else if (MccSchema.TABLE_REQUEST_REVIEWS.equalsIgnoreCase(name))
        {
            CustomPermissionsTable<?> ret = new CustomPermissionsTable<>(this, schemaTable, cf);
            ret.addPermissionMapping(ReadPermission.class, MccRabReviewPermission.class);
            ret.addPermissionMapping(InsertPermission.class, MccRabReviewPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, MccRabReviewPermission.class);
            ret.addPermissionMapping(DeletePermission.class, MccRabReviewPermission.class);

            ret = ret.init();

            return ret;
        }
        else if (MccSchema.TABLE_REQUEST_SCORE.equalsIgnoreCase(name))
        {
            CustomPermissionsTable<?> ret = new CustomPermissionsTable<>(this, schemaTable, cf);
            ret.addPermissionMapping(ReadPermission.class, MccViewRequestsPermission.class);
            ret.addPermissionMapping(InsertPermission.class, MccViewRequestsPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, MccViewRequestsPermission.class);
            ret.addPermissionMapping(DeletePermission.class, MccRequestAdminPermission.class);

            ret = ret.init();

            return addScoreColumns(ret);
        }
        else if (MccSchema.TABLE_CENSUS.equalsIgnoreCase(name))
        {
            CustomPermissionsTable<?> ret = new CustomPermissionsTable<>(this, schemaTable, cf);
            ret.addPermissionMapping(InsertPermission.class, MccDataAdminPermission.class);
            ret.addPermissionMapping(UpdatePermission.class, MccDataAdminPermission.class);
            ret.addPermissionMapping(DeletePermission.class, MccDataAdminPermission.class);

            return ret.init();
        }

        return super.createWrappedTable(name, schemaTable, cf);
    }

    @Override
    public Set<String> getTableNames()
    {
        Set<String> available = new CaseInsensitiveTreeSet();
        available.addAll(super.getTableNames());
        addAggregatedTableNames(available);

        return available;
    }

    private static final String TABLE_AGGREGATED_WEIGHT = "aggregatedWeight";
    private static final String TABLE_AGGREGATED_KINSHIP = "aggregatedKinship";
    private static final String TABLE_AGGREGATED_DEPARTURES = "aggregatedDepartures";
    private static final String TABLE_AGGREGATED_OBS = "aggregatedObservations";
    private static final String TABLE_AGGREGATED_GENOMICS = "aggregatedGenomicDatasets";
    private static final String TABLE_AGGREGATED_CENSUS = "aggregatedCensus";

    @Override
    public Set<String> getVisibleTableNames()
    {
        Set<String> available = new CaseInsensitiveTreeSet();
        available.addAll(super.getVisibleTableNames());
        addAggregatedTableNames(available);

        return Collections.unmodifiableSet(available);
    }

    private boolean supportsAggregatedTables()
    {
        return getContainer().hasPermission(getUser(), MccDataAdminPermission.class) && MccManager.get().getMCCInternalDataContainer(getContainer()) != null;
    }

    private void addAggregatedTableNames(Set<String> available)
    {
        if (supportsAggregatedTables())
        {
            available.add(TABLE_AGGREGATED_KINSHIP);
            available.add(TABLE_AGGREGATED_WEIGHT);
            available.add(TABLE_AGGREGATED_DEPARTURES);
            available.add(TABLE_AGGREGATED_OBS);
            available.add(TABLE_AGGREGATED_GENOMICS);
            available.add(TABLE_AGGREGATED_CENSUS);
        }
    }

    private TableInfo getKinshipQuery()
    {
        String template = "SELECT\n" +
                "    d.Id.mccAlias.externalAlias as Id,\n" +
                "    d.Id as originalId,\n" +
                "    d.date,\n" +
                "    d.Id2MccAlias.externalAlias as Id2,\n" +
                "    d.Id2 as originalId2,\n" +
                "    d.kinship,\n" +
                "    d.relationship,\n" +
                "    d.objectid,\n" +
                "    d.container\n" +
                "\n" +
                "FROM \"<CONTAINER_PATH>\".study.kinship d WHERE d.qcstate.publicdata = true\n";

        return makeAggregatedQuery(TABLE_AGGREGATED_KINSHIP, template);
    }

    private TableInfo getWeightQuery()
    {
        String template = "SELECT\n" +
                "    d.Id.mccAlias.externalAlias as Id,\n" +
                "    d.Id as originalId,\n" +
                "    d.date,\n" +
                "    d.weight,\n" +
                "    d.objectid,\n" +
                "    d.container\n" +
                "\n" +
                "FROM \"<CONTAINER_PATH>\".study.weight d WHERE d.qcstate.publicdata = true\n";

        return makeAggregatedQuery(TABLE_AGGREGATED_WEIGHT, template);
    }

    private TableInfo getCensusQuery()
    {
        String template = "SELECT\n" +
                "    d.yearNo,\n" +
                "    d.startdate,\n" +
                "    d.enddate,\n" +
                "    d.centerName,\n" +
                "    d.totalBreedingPairs,\n" +
                "    d.totalLivingOffspring,\n" +
                "    d.survivalRates,\n" +
                "    d.marmosetsShipped,\n" +
                "    d.container\n" +
                "\n" +
                "FROM \"<CONTAINER_PATH>\".mcc.census d\n";

        return makeAggregatedQuery(TABLE_AGGREGATED_CENSUS, template);
    }

    private TableInfo getDepartureQuery()
    {
        String template = "SELECT\n" +
                "    d.Id.mccAlias.externalAlias as Id,\n" +
                "    d.Id as originalId,\n" +
                "    d.date,\n" +
                "    d.source,\n" +
                "    d.destination,\n" +
                "    d.mccTransfer,\n" +
                "    d.objectid,\n" +
                "    d.container\n" +
                "\n" +
                "FROM \"<CONTAINER_PATH>\".study.departure d WHERE d.qcstate.publicdata = true AND d.mccTransfer = true\n";

        return makeAggregatedQuery(TABLE_AGGREGATED_DEPARTURES, template);
    }

    private TableInfo getObsQuery()
    {
        String template = "SELECT\n" +
                "    d.Id.mccAlias.externalAlias as Id,\n" +
                "    d.Id as originalId,\n" +
                "    d.date,\n" +
                "    d.category,\n" +
                "    d.observation,\n" +
                "    d.objectid,\n" +
                "    d.container\n" +
                "\n" +
                "FROM \"<CONTAINER_PATH>\".study.clinical_observations d WHERE d.qcstate.publicdata = true\n";

        return makeAggregatedQuery(TABLE_AGGREGATED_OBS, template);
    }

    private TableInfo getGenomicsQuery()
    {
        String template = "SELECT\n" +
                "    d.Id.mccAlias.externalAlias as Id,\n" +
                "    d.Id as originalId,\n" +
                "    d.date,\n" +
                "    d.datatype,\n" +
                "    d.sra_accession,\n" +
                "    d.objectid,\n" +
                "    d.container\n" +
                "\n" +
                "FROM \"<CONTAINER_PATH>\".study.genomicDatasets d WHERE d.qcstate.publicdata = true\n";

        return makeAggregatedQuery(TABLE_AGGREGATED_GENOMICS, template);
    }

    private TableInfo makeAggregatedQuery(String queryName, String sqlTemplate)
    {
        if (!getContainer().hasPermission(getUser(), MccDataAdminPermission.class))
        {
            return null;
        }

        QueryDefinition qd = QueryService.get().createQueryDef(getUser(), getContainer(), this, queryName);
        StringBuilder sql = new StringBuilder();

        Container parent = MccManager.get().getMCCInternalDataContainer(getContainer());
        if (parent == null)
        {
            return null;
        }

        String unionClause = "";
        for (Container c : parent.getChildren())
        {
            sql.append(unionClause);
            unionClause = " UNION ALL ";
            sql.append(sqlTemplate.replaceAll("<CONTAINER_PATH>", c.getPath()));
        }

        qd.setSql(sql.toString());

        List<QueryException> errors = new ArrayList<QueryException>();
        TableInfo ti = qd.getTable(errors, true);
        if (!errors.isEmpty())
        {
            _log.error("Problem with aggregated query: " + queryName);
            for (QueryException e : errors)
            {
                _log.error(e.getMessage());
            }
        }

        return ti;
    }

    private CustomPermissionsTable<?> addScoreColumns(CustomPermissionsTable<?> ti)
    {
        if (ti.getColumn("rabReviewStatus") == null)
        {
            SQLFragment sql = new SQLFragment("(SELECT CONCAT(COALESCE(CAST(sum(CASE WHEN r.review IS NULL THEN 0 ELSE 1 END) as varchar), '0'), ' of ', cast(count(*) as varchar), ' completed') as expr FROM mcc.requestReviews r WHERE r.requestId = " + ExprColumn.STR_TABLE_ALIAS + ".requestId)");
            ExprColumn newCol = new ExprColumn(ti, "rabReviewStatus", sql, JdbcType.VARCHAR, ti.getColumn("requestId"));
            newCol.setSortFieldKeys(Collections.singletonList(FieldKey.fromString("pendingRabReviews")));

            newCol.setLabel("RAB Review Status");
            newCol.setDisplayColumnFactory(colInfo -> {
                return new DataColumn(colInfo)
                {
                    @Override
                    public boolean isFilterable()
                    {
                        return false;
                    }

                    @Override
                    public boolean isSortable()
                    {
                        return false;
                    }
                };
            });

            ti.addColumn(newCol);

            SQLFragment sql2 = new SQLFragment("(SELECT COALESCE(sum(CASE WHEN r.review IS NULL THEN 1 ELSE 0 END), -1) as expr FROM mcc.requestReviews r WHERE r.requestId = " + ExprColumn.STR_TABLE_ALIAS + ".requestId)");
            ExprColumn newCol2 = new ExprColumn(ti, "pendingRabReviews", sql2, JdbcType.INTEGER, ti.getColumn("requestId"));
            newCol2.setLabel("Pending RAB Reviews");
            ti.addColumn(newCol2);
        }

        return ti;
    }
}
