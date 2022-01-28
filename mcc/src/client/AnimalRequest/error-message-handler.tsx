import React from 'react'
import { nanoid } from 'nanoid'

export default function ErrorMessageHandler(props) {
  const ref = React.useRef()
	const [errors, setErrors] = React.useState(new Set())

	React.useEffect(() => {
    if (ref.current && props.isSubmitting) {
      const el = ref.current
      
      const handleValidationMessages = () => {
        const tmp = new Set()
        
        for (const e of el.querySelectorAll('input, select, textarea')) {
          if (!e.checkValidity()) {
            tmp.add(`${e.name}: ${e.validationMessage}`)
          }
        }
        
        setErrors(tmp)
      }
      
      el.addEventListener('input', handleValidationMessages)
      handleValidationMessages()
    }
  }, [props.isSubmitting, props.rerender]);

  return (
    <>
      <span ref={ref} key={props.id + "-error-message-handler-children"}>
        {props.children}
      </span>
      <ul className="tw-mx-2 tw-mb-6" key={props.id + "-error-message-handler-errors"}>
        {[...errors].map(txt => <li className="tw-tracking-wide tw-text-red-500" key={nanoid()}>{txt}</li>)}
      </ul>
    </>
  )
}
