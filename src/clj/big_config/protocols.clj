(ns big-config.protocols)

(defprotocol StepExecutor
  (execute-step [this step-info])
  (handle-exit [this opts]))

(defprotocol Workflow
  (run-workflow [this executor opts])
  (get-steps [this]))

(defprotocol Command
  (execute [this executor opts]))

