(ns big-config.commands
  (:require
   [big-config.lock :as lock]
   [big-config.unlock :as unlock]
   [big-config.protocols :refer [Command run-workflow handle-exit]]
   [big-config.workflows :refer [->RunWithLockWorkflow ->RunWorkflow]]))

(defrecord DestroyCommand []
  Command
  
  (execute [_this executor opts]
      (run-workflow (->RunWithLockWorkflow) executor opts)))

(defrecord ApplyCommand []
  Command
  
  (execute [_ executor opts]
    (run-workflow (->RunWithLockWorkflow) executor opts)))

(defrecord SimpleWorkflowCommand [workflow]
  Command
  
  (execute [_ executor opts]
    (run-workflow workflow executor opts)))

(defrecord LockCommand []
  Command
  
  (execute [_ executor opts]
    (handle-exit executor 
                (lock/lock executor opts))))

(defrecord UnlockAnyCommand []
  Command
  
  (execute [_ executor opts]
    (handle-exit executor 
                (unlock/unlock-any executor opts)))) 