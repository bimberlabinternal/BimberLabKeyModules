import React from 'react';

import Input from './input';
import Title from './title';
import ErrorMessageHandler from './error-message-handler';
import { CoInvestigatorModel } from '../../components/RequestUtils';

export default function CoInvestigators(props: {coinvestigators: CoInvestigatorModel[], required: boolean, isSubmitting: boolean, onAddRecord: () => void, onRemoveRecord: (x: CoInvestigatorModel) => void}) {

  return (
      <>
        {[...props.coinvestigators].map((coInvestigator, index) => (
            <ErrorMessageHandler isSubmitting={props.isSubmitting} keyInternal={coInvestigator.uuid} key={coInvestigator.uuid + "-errorHandler"}>
              <div className="tw-flex tw-flex-wrap tw-w-full tw-mx-2 tw-mb-4" key={coInvestigator.uuid}>
                <div className="tw-flex tw-flex-wrap tw-w-full tw-mb-6">
                  <div className="tw-w-1/2 md:tw-mb-0">
                    <Title text={ "Co-Investigator " + (index + 1)} />
                  </div>

                  <div className="tw-flex tw-flex-wrap tw-w-full md:tw-w-1/2 md:tw-mb-0">
                    <input type="button" className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-2 tw-px-4 tw-border-none tw-rounded" onClick={() => props.onRemoveRecord(coInvestigator)} value="Remove" />
                  </div>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 md:tw-mb-0">
                  <Input id={"coinvestigators-" + index + "-" + "lastName"} ariaLabel={"Last Name" + "#" + index} isSubmitting={props.isSubmitting} placeholder="Last Name" required={props.required} defaultValue={coInvestigator.lastname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                  <Input id={"coinvestigators-" + index + "-" + "firstName"}  ariaLabel={"First Name" + "#" + index} isSubmitting={props.isSubmitting} placeholder="First Name" required={props.required} defaultValue={coInvestigator.firstname}/>
                </div>

                <div className="tw-w-full md:tw-w-1/3 tw-px-3 tw-mb-6 md:tw-mb-0">
                  <Input id={"coinvestigators-" + index + "-" + "middleInitial"}  ariaLabel={"Middle Initial" + "#" + index} isSubmitting={props.isSubmitting} placeholder="Middle Initial" required={false} defaultValue={coInvestigator.middleinitial} maxLength="8"/>
                </div>

                <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
                  <Input id={"coinvestigators-" + index + "-" + "institution"}  ariaLabel={"Institution" + "#" + index} isSubmitting={props.isSubmitting} placeholder="Institution" required={props.required} defaultValue={coInvestigator.institutionname}/>
                </div>
              </div>
            </ErrorMessageHandler>
        ))}

        <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
          <input type="button" className="tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-2 tw-mt-2 tw-px-4 tw-border-none tw-rounded" onClick={props.onAddRecord} value="Add Co-investigator" />
        </div>
      </>
  )
}
