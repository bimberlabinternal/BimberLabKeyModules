import React from 'react'
import { nanoid } from 'nanoid'

export default function ErrorMessageHandler(props) {
  const ref = React.useRef(null)
  const [errors, setErrors] = React.useState(new Set())

  React.useEffect(() => {
    if (ref && ref.current && props.isSubmitting) {
      const el = ref.current as HTMLElement
      if (!el) {
        return
      }

      const handleValidationMessages = () => {
        const tmp = new Set()
        el.querySelectorAll<HTMLSelectElement>('input, select, textarea').forEach(function(e){
          if (!e.checkValidity()) {
            const name = e.getAttribute("aria-label").split("#")[0]
            const message = e.validationMessage
            tmp.add(`${name}: ${message}`)
          }
        })

        setErrors(tmp)
      }

      el.addEventListener('input', handleValidationMessages)
      handleValidationMessages()
    } else {
      // NOTE: if we're not actively submitting, reset the errors
      setErrors(new Set())
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
