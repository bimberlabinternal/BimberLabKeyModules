import React, { useState, Fragment } from 'react'
import { nanoid } from 'nanoid'

import Input from './input'
import Title from './title'
import ErrorMessageHandler from './error-message-handler'

export default function CoInvestigators(props) {
  const [coInvestigators, setCoInvestigators] = useState(new Set(
    props.defaultValue.map((coInvestigator) => ({
      ...coInvestigator,
      "uuid": nanoid()
    }))
  ))
 
  function addInvestigator() {
    coInvestigators.add({
      "uuid": nanoid()
    })
    setCoInvestigators(new Set(coInvestigators))
  }

  function removeCoInvestigator(coInvestigator) {
    coInvestigators.delete(coInvestigator)
    setCoInvestigators(new Set(coInvestigators))
  }

  return (
    <>
      {[...coInvestigators].map((coInvestigator, index) => (
        <ErrorMessageHandler isSubmitting={props.isSubmitting} keyInternal={coInvestigator.uuid} key={coInvestigator.uuid + "-errorHandler"}>
        <div className="tw-flex tw-flex-wrap tw-w-full tw-mx-2 tw-mb-4" key={coInvestigator.uuid}>
          <div className="tw-flex tw-flex-wrap tw-w-full tw-mb-6">
            <div className="tw-w-1/2 md:tw-mb-0">
              <Title text={ "Co-Investigator " + (index + 1)} />
            </div>

            <div className="tw-flex tw-flex-wrap tw-w-full md:tw-w-1/2 md:tw-mb-0">
                <input type="button" className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-2 tw-px-4 tw-border-none tw-rounded" onClick={() => removeCoInvestigator(coInvestigator)} value="Remove" />
            </div>
          </div>

          <div className="tw-w-full md:tw-w-1/3 tw-px-3 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "lastName"} name="Last Name" isSubmitting={props.isSubmitting} placeholder="Last Name" required={props.required} defaultValue={coInvestigator.lastname}/>
          </div>

          <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "firstName"} name="First Name" isSubmitting={props.isSubmitting} placeholder="First Name" required={props.required} defaultValue={coInvestigator.firstname}/>
          </div>

          <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "middleInitial"} name="Middle Initial" isSubmitting={props.isSubmitting} placeholder="Middle Initial" required={false} defaultValue={coInvestigator.middleinitial}/>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <Input id={"coinvestigators-" + index + "-" + "institution"} name="Institution" isSubmitting={props.isSubmitting} placeholder="Institution" required={props.required} defaultValue={coInvestigator.institutionname}/>
          </div>
        </div>
        </ErrorMessageHandler>
      ))}

      <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
        <input type="button" className="tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-2 tw-mt-2 tw-px-4 tw-border-none tw-rounded" onClick={addInvestigator} value="Add Co-investigator" />
      </div>
    </>
  )
}
