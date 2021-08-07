import React, { useState, useReducer } from 'react';


import Input from './input'
import CoInvestigatorTable from './co-investigator-table'


type Action = {
  type: "ADD"
  firstName: string
  lastName: string
  MI: string
  institution: string
} | {
  type: "REMOVE"
  index: number
}


function reducer(coInvestigators, action: Action) {
    switch (action.type) {
      case "ADD":
        coInvestigators.push({firstName: action.firstName, lastName: action.lastName, MI: action.MI, institution: action.institution})
        break
      case "REMOVE":
        coInvestigators.splice(action.index, 1)
        break
      default:
    }
  
    return [...coInvestigators]
  }
  
  export default function CoInvestigators() {
    const [tempFirstName, setTempFirstName] = useState("")
    const [tempLastName, setTempLastName] = useState("")
    const [tempMI, setTempMI] = useState("")
    const [tempInstitution, setTempInstitution] = useState("")

    const [errorMessage, setErrorMessage] = useState("")
    const [coInvestigators, dispatch] = useReducer(reducer, [])
  
    function addInvestigator() {
      if (tempFirstName && tempLastName && tempMI && tempInstitution) {
        dispatch({type: "ADD", lastName: tempLastName, firstName: tempFirstName, MI: tempMI, institution: tempInstitution})
        setTempFirstName("")
        setTempLastName("")
        setTempMI("")
        setTempInstitution("")
        setErrorMessage("")
      } else {
        setErrorMessage("Co-investigator must have a first name, last name, middle initial, and institution.")
      }
    }
  
    return (
      <>
        <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
          <Input id="investigator-last-name" placeholder="Last Name" value={tempLastName} onChange={(e) => setTempLastName(e.currentTarget.value)} />
        </div>

        <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
          <Input id="investigator-first-name" placeholder="First Name" value={tempFirstName} onChange={(e) => setTempFirstName(e.currentTarget.value)} />
        </div>

        <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
          <Input id="investigator-middle-initial" placeholder="Middle Initial" value={tempMI} onChange={(e) => setTempMI(e.currentTarget.value)} />
        </div>

        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
          <Input id="investigator-email" placeholder="Email Address" value={tempInstitution} onChange={(e) => setTempInstitution(e.currentTarget.value)} />
        </div>

        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
          {errorMessage && <p className="tw-p-4 tw-text-red-700">{errorMessage}</p>}
        </div>

        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
          <input type="button" className="tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-2 tw-px-4 tw-border-none tw-rounded" onClick={addInvestigator} value="Add" />
        </div>

        <CoInvestigatorTable coInvestigators={coInvestigators} onRemove={(index) => dispatch({type: "REMOVE", index})}/>
      </>
    );
  }