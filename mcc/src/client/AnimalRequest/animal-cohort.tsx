import React, { useState, Fragment } from 'react'
import { nanoid } from 'nanoid'

import InputNumber from './input-number'
import Select from './select'
import Title from './title'
import TextArea from './text-area'

import { animalSexes, otherCharacteristicsPlaceholder } from './values'

export default function AnimalCohorts(props) {
  const [cohorts, setCohorts] = useState(new Set(
    props.defaultValue.map((cohort) => ({
      ...cohort,
      "uuid": nanoid()
    }))
  ))
 
  function addCohort() {
    cohorts.add({
      "uuid": nanoid()
    })
    setCohorts(new Set(cohorts))
  }

  function removeCohort(cohort) {
    if(cohorts.size > 1) {
      cohorts.delete(cohort)
      setCohorts(new Set(cohorts))
    }
  }

  return (
    <>
      {[...cohorts].map((cohort, index) => (
        <div className="tw-flex tw-flex-wrap tw-w-full tw-mx-2 tw-mb-1" key={cohort.uuid}>
          <div className="tw-flex tw-flex-wrap tw-w-full tw-mb-6">
            <div className="tw-w-1/2 md:tw-mb-0">
              <Title text={ "Cohort " + (index + 1)} />
            </div>

            <div className="tw-flex tw-flex-wrap tw-w-full md:tw-w-1/2 md:tw-mb-0">
                <input type="button" className="tw-ml-auto tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-2 tw-px-4 tw-border-none tw-rounded"
                  onClick={() => removeCohort(cohort)} value="Remove" style={{display: cohorts.size > 1 ? null : "none"}}/>
            </div>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <Title text="Number of animals&nbsp;&nbsp;&nbsp;" />
            <div className="tw-mb-4">
              <InputNumber id={"animal-cohorts-" + index + "-" + "numberofanimals"} isSubmitting={props.isSubmitting} placeholder="Number of animals" required={true} defaultValue={cohort.numberofanimals}/>
            </div>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <Title text="Cohort sex&nbsp;&nbsp;&nbsp;" />
            <div className="tw-mb-6">
              <Select id={"animal-cohorts-" + index + "-" + "sex"} isSubmitting={props.isSubmitting} options={animalSexes} defaultValue={cohort.sex}/>
            </div>
          </div>

          <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
            <TextArea id={"animal-cohorts-" + index + "-" + "othercharacteristics"} isSubmitting={props.isSubmitting} placeholder={otherCharacteristicsPlaceholder} 
             required={true} defaultValue={cohort.othercharacteristics}/>
          </div>
        </div>
      ))}

      <div className="tw-w-full tw-px-3 tw-mb-6 md:tw-mb-0">
        <input type="button" className="tw-bg-blue-500 hover:tw-bg-blue-400 tw-text-white tw-font-bold tw-py-2 tw-mt-2 tw-px-4 tw-border-none tw-rounded" onClick={addCohort} value="Add Cohort" />
      </div>
    </>
  )
}':wait'
