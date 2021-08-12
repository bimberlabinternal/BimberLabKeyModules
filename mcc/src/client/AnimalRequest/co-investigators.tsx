import React, { useState, Fragment } from 'react'
import { nanoid } from 'nanoid'

import Input from './input'


export default function CoInvestigators(props) {
  const [coInvestigators, setCoInvestigators] = useState(new Set([nanoid()]))
 
  function addInvestigator() {
    coInvestigators.add(nanoid())
    setCoInvestigators(new Set(coInvestigators))
  }

  function removeCoInvestigator(uuid) {
    console.log(uuid)
    coInvestigators.delete(uuid)
    setCoInvestigators(new Set(coInvestigators))
  }

  return (
    <>
      {[...coInvestigators].map((uuid, index) => (
        <div className="tw-flex tw-flex-wrap tw-w-full tw-mx-2 tw-mb-6" key={uuid}>
          <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "lastName"} placeholder="Last Name" />
          </div>

          <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "firstName"} placeholder="First Name" />
          </div>

          <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "middleInitial"} placeholder="Middle Initial" />
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "institution"} placeholder="Institution" />
          </div>

          {coInvestigators.size > 1 && 
            <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
              <input type="button" className="tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-2 tw-mt-2 tw-px-4 tw-border-none tw-rounded" onClick={() => removeCoInvestigator(uuid)} value="Remove" />
            </div>
          }
        </div>
      ))}

      <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
        <input type="button" className="tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-2 tw-mt-2 tw-px-4 tw-border-none tw-rounded" onClick={addInvestigator} value="Add" />
      </div>
    </>
  )
}
