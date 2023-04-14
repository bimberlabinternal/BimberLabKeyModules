package org.labkey.mcc.query;

import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableSelector;
import org.labkey.api.query.FieldKey;
import org.labkey.mcc.MccSchema;

import java.util.Date;

public class MccRequest
{
    private int _rowId;
    private String _title;
    private String _narrative;
    private String _neuroscience;
    private String _diseasefocus;
    private boolean _census;
    private String _censusReason;

    private String _lastname;
    private String _firstname;
    private String _middleinitial;
    private Boolean _isprincipalinvestigator;

    private boolean _terminalProcedures;
    private String _institutionname;
    private String _institutioncity;
    private String _institutionstate;
    private String _institutioncountry;
    private String _institutiontype;
    private String _officiallastname;
    private String _officialfirstname;
    private String _officialemail;
    private String _fundingsource;
    private String _grantnumber;

    private Date _applicationduedate;
    private String _experimentalrationale;
    private String _methodsproposed;
    private String _collaborations;
    private String _breedinganimals;
    private String _breedingpurpose;
    private Boolean _existingmarmosetcolony;
    private Boolean _existingnhpfacilities;
    private String _animalwelfare;
    private Boolean _certify;
    private String _vetlastname;
    private String _vetfirstname;
    private String _vetemail;
    private String _iacucapproval;
    private String _iacucprotocol;
    private String _status;

    private String _objectId;
    private String _container;
    private Date _created;
    private int _createdby;
    private Date _modified;
    private int _modifiedby;

    public MccRequest()
    {

    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLastname()
    {
        return _lastname;
    }

    public void setLastname(String lastname)
    {
        _lastname = lastname;
    }

    public String getFirstname()
    {
        return _firstname;
    }

    public void setFirstname(String firstname)
    {
        _firstname = firstname;
    }

    public String getMiddleinitial()
    {
        return _middleinitial;
    }

    public void setMiddleinitial(String middleinitial)
    {
        _middleinitial = middleinitial;
    }

    public Boolean getIsprincipalinvestigator()
    {
        return _isprincipalinvestigator;
    }

    public void setIsprincipalinvestigator(Boolean isprincipalinvestigator)
    {
        _isprincipalinvestigator = isprincipalinvestigator;
    }

    public String getInstitutionname()
    {
        return _institutionname;
    }

    public void setInstitutionname(String institutionname)
    {
        _institutionname = institutionname;
    }

    public String getInstitutioncity()
    {
        return _institutioncity;
    }

    public void setInstitutioncity(String institutioncity)
    {
        _institutioncity = institutioncity;
    }

    public String getInstitutionstate()
    {
        return _institutionstate;
    }

    public void setInstitutionstate(String institutionstate)
    {
        _institutionstate = institutionstate;
    }

    public String getInstitutioncountry()
    {
        return _institutioncountry;
    }

    public void setInstitutioncountry(String institutioncountry)
    {
        _institutioncountry = institutioncountry;
    }

    public String getInstitutiontype()
    {
        return _institutiontype;
    }

    public void setInstitutiontype(String institutiontype)
    {
        _institutiontype = institutiontype;
    }

    public String getOfficiallastname()
    {
        return _officiallastname;
    }

    public void setOfficiallastname(String officiallastname)
    {
        _officiallastname = officiallastname;
    }

    public String getOfficialfirstname()
    {
        return _officialfirstname;
    }

    public void setOfficialfirstname(String officialfirstname)
    {
        _officialfirstname = officialfirstname;
    }

    public String getOfficialemail()
    {
        return _officialemail;
    }

    public void setOfficialemail(String officialemail)
    {
        _officialemail = officialemail;
    }

    public String getFundingsource()
    {
        return _fundingsource;
    }

    public void setFundingsource(String fundingsource)
    {
        _fundingsource = fundingsource;
    }

    public String getGrantnumber()
    {
        return _grantnumber;
    }

    public void setGrantnumber(String grantnumber)
    {
        _grantnumber = grantnumber;
    }

    public Date getApplicationduedate()
    {
        return _applicationduedate;
    }

    public void setApplicationduedate(Date applicationduedate)
    {
        _applicationduedate = applicationduedate;
    }

    public String getExperimentalrationale()
    {
        return _experimentalrationale;
    }

    public void setExperimentalrationale(String experimentalrationale)
    {
        _experimentalrationale = experimentalrationale;
    }

    public String getMethodsproposed()
    {
        return _methodsproposed;
    }

    public void setMethodsproposed(String methodsproposed)
    {
        _methodsproposed = methodsproposed;
    }

    public String getCollaborations()
    {
        return _collaborations;
    }

    public void setCollaborations(String collaborations)
    {
        _collaborations = collaborations;
    }

    public String getBreedinganimals()
    {
        return _breedinganimals;
    }

    public void setBreedinganimals(String breedinganimals)
    {
        _breedinganimals = breedinganimals;
    }

    public String getBreedingpurpose()
    {
        return _breedingpurpose;
    }

    public void setBreedingpurpose(String breedingpurpose)
    {
        _breedingpurpose = breedingpurpose;
    }

    public Boolean getExistingmarmosetcolony()
    {
        return _existingmarmosetcolony;
    }

    public void setExistingmarmosetcolony(Boolean existingmarmosetcolony)
    {
        _existingmarmosetcolony = existingmarmosetcolony;
    }

    public Boolean getExistingnhpfacilities()
    {
        return _existingnhpfacilities;
    }

    public void setExistingnhpfacilities(Boolean existingnhpfacilities)
    {
        _existingnhpfacilities = existingnhpfacilities;
    }

    public String getAnimalwelfare()
    {
        return _animalwelfare;
    }

    public void setAnimalwelfare(String animalwelfare)
    {
        _animalwelfare = animalwelfare;
    }

    public Boolean getCertify()
    {
        return _certify;
    }

    public void setCertify(Boolean certify)
    {
        _certify = certify;
    }

    public String getVetlastname()
    {
        return _vetlastname;
    }

    public void setVetlastname(String vetlastname)
    {
        _vetlastname = vetlastname;
    }

    public String getVetfirstname()
    {
        return _vetfirstname;
    }

    public void setVetfirstname(String vetfirstname)
    {
        _vetfirstname = vetfirstname;
    }

    public String getVetemail()
    {
        return _vetemail;
    }

    public void setVetemail(String vetemail)
    {
        _vetemail = vetemail;
    }

    public String getIacucapproval()
    {
        return _iacucapproval;
    }

    public void setIacucapproval(String iacucapproval)
    {
        _iacucapproval = iacucapproval;
    }

    public String getIacucprotocol()
    {
        return _iacucprotocol;
    }

    public void setIacucprotocol(String iacucprotocol)
    {
        _iacucprotocol = iacucprotocol;
    }

    public String getStatus()
    {
        return _status;
    }

    public void setStatus(String status)
    {
        _status = status;
    }

    public String getObjectId()
    {
        return _objectId;
    }

    public void setObjectId(String objectId)
    {
        _objectId = objectId;
    }

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Date getCreated()
    {
        return _created;
    }

    public void setCreated(Date created)
    {
        _created = created;
    }

    public int getCreatedby()
    {
        return _createdby;
    }

    public void setCreatedby(int createdby)
    {
        _createdby = createdby;
    }

    public Date getModified()
    {
        return _modified;
    }

    public void setModified(Date modified)
    {
        _modified = modified;
    }

    public int getModifiedby()
    {
        return _modifiedby;
    }

    public void setModifiedby(int modifiedby)
    {
        _modifiedby = modifiedby;
    }

    public static MccRequest getForId(String objectId)
    {
        return new TableSelector(MccSchema.getInstance().getSchema().getTable(MccSchema.TABLE_ANIMAL_REQUESTS)).getObject(objectId, MccRequest.class);
    }

    public static MccRequest getForRowId(int rowId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromString("rowid"), rowId);

        return new TableSelector(MccSchema.getInstance().getSchema().getTable(MccSchema.TABLE_ANIMAL_REQUESTS), filter, null).getObject(MccRequest.class);
    }
}
