(ns big-config.executors
  (:require
   [big-config :as bc]
   [big-config.msg :refer [step->message]]
   [big-config.protocols :refer [StepExecutor]]
   [big-config.utils :refer [exit-with-code]]
   [bling.core :refer [bling]]))

(defrecord DefaultStepExecutor []
  StepExecutor
  (execute-step [_ step-info]
    (let [{:keys [f step opts]} step-info
          opts (update opts ::bc/steps (fnil conj []) step)
          msg (step->message step)]
      (when msg
        (binding [*out* *err*]
          (println msg)))
      (f opts)))
  
  (handle-exit [_ {:keys [::bc/exit ::bc/env ::bc/err] :as opts}]
    (when (and (not= exit 0) (string? err))
      (binding [*out* *err*]
        (println (bling [:red.bold err]))))
    (case env
      :shell (exit-with-code exit)
      :repl opts))) 