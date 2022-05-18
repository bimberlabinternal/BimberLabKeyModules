import React from 'react';

import InputNumber from './input-number';
import Select from './select';
import Title from './title';
import TextArea from './text-area';
import ErrorMessageHandler from './error-message-handler';

import { animalSexes, otherCharacteristicsPlaceholder } from './values';
import { AnimalCohort } from '../../components/RequestUtils';

export default function AnimalCohorts(props: { cohorts: AnimalCohort[], required: boolean, isSubmitting: boolean, onAddCohort: () => void, onRemoveCohort: (x: AnimalCohort) => void}) {
  return (
    <>
      {[...props.cohorts].map((cohort, index) => (
        <ErrorMessageHandler isSubmitting={props.isSubmitting} keyInternal={cohort.uuid} key={cohort.uuid + "-errorHandler"}>
        <div className="tw-flex tw-flex-wrap tw-w-full tw-mx-2 tw-mb-10" key={cohort.uuid}>
          <div className="tw-flex tw-flex-wrap tw-w-full tw-mb-6">
            <div className="tw-w-1/2 md:tw-mb-0">
              <Title text={ "Cohort " + (index + 1)} />
            </div>

            <div className="tw-flex tw-flex-wrap tw-w-full md:tw-w-1/2 md:tw-mb-0">
                <input type="button" className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-2 tw-px-4 tw-border-none tw-rounded"
                  onClick={() => props.onRemoveCohort(cohort)} value="Remove" style={{display: index > 0 ? null : "none"}}/>
            </div>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <Title text="Number of animals&nbsp;&nbsp;&nbsp;" />
            <div className="tw-mb-4">
              <InputNumber id={"animal-cohorts-" + index + "-" + "numberofanimals"} ariaLabel={"Number of Animals" + "#" + index} isSubmitting={props.isSubmitting} placeholder="Number of animals" required={props.required} defaultValue={cohort.numberofanimals}/>
            </div>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <Title text="Cohort sex&nbsp;&nbsp;&nbsp;" />
            <div className="tw-mb-6">
              <Select id={"animal-cohorts-" + index + "-" + "sex"} ariaLabel={"Sex" + "#" + index} isSubmitting={props.isSubmitting} options={animalSexes} defaultValue={cohort.sex} required={props.required}/>
            </div>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <TextArea id={"animal-cohorts-" + index + "-" + "othercharacteristics"} ariaLabel={"Other Characteristics" + "#" + index} isSubmitting={props.isSubmitting} placeholder={otherCharacteristicsPlaceholder}
             required={props.required} defaultValue={cohort.othercharacteristics}/>
          </div>
        </div>
        </ErrorMessageHandler>
      ))}

      <div className="tw-w-full tw-px-3 tw-mb-10 md:tw-mb-0">
        <input type="button" className="tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-2 tw-mt-2 tw-px-4 tw-border-none tw-rounded" onClick={props.onAddCohort} value="Add Cohort" />
      </div>
    </>
  )
}
