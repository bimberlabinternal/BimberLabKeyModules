import React from 'react';

export default function CoInvestigatorTable(props) {
    if(props.coInvestigators.length > 0) {
      return (
        <>
        <table className="tw-rounded-t-lg tw-m-5 tw-w-5/6 tw-mx-auto tw-bg-gray-200 tw-text-gray-800">
        <tbody>

        <tr className="tw-text-left tw-border-b-2 tw-border-gray-300">
          <th className="tw-px-4 tw-py-3">Name</th>
          <th className="tw-px-4 tw-py-3">Institution</th>
          <th className="tw-px-4 tw-py-3"></th>
        </tr>

        {props.coInvestigators.map((coInvestigator, index) => (
          <tr key={index} className="tw-bg-gray-100 tw-border-b tw-border-gray-200">
            <td className="tw-px-4 tw-py-3">
              {coInvestigator.firstName} {coInvestigator.MI}. {coInvestigator.lastName}
            </td>

            <td className="tw-px-4 tw-py-3">
              {coInvestigator.institution}
            </td>

            <td className="tw-px-4 tw-py-3">
              <input type="button" className="tw-bg-red-500 hover:tw-bg-red-400 tw-text-white tw-font-bold tw-py-2 tw-px-4 tw-border-none tw-rounded" value="Remove" onClick={() => props.onRemove(index)} />
            </td>
          </tr>
        ))}

        </tbody>
        </table>
        </>
      )
    } else {
      return(
        <>
        </>
      )
    }
}