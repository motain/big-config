(ns big-config.main
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.commands :refer [->ApplyCommand ->LockCommand
                                ->DestroyCommand ->SimpleWorkflowCommand
                                ->UnlockAnyCommand]]
   [big-config.executors :refer [->DefaultStepExecutor]]
   [big-config.protocols :refer [execute execute-step]]
   [big-config.utils :refer [step->workflow]]
   [big-config.workflows :refer [->RunWorkflow]]
   [clojure.pprint :as pp]))

(defn executor->step-fn [executor]
  (fn [{:keys [f step opts]}]
    (execute-step executor {:f f :step step :opts opts})))

(defn ^:export tofu [{[cmd-key module profile] :args
                      env :env
                      executor :executor
                      :or {executor (->DefaultStepExecutor)}}]
  (let [step-fn (executor->step-fn executor)
        read-module (step->workflow aero/read-module ::aero/read-module)
        opts {::bc/cmd (name cmd-key)
              ::bc/env (or env :shell)
              ::aero/config "big-config.edn"
              ::aero/module module
              ::aero/profile profile}
        opts (read-module step-fn opts)
        cmd (case cmd-key
              :opts    nil
              (:init :plan) (->SimpleWorkflowCommand (->RunWorkflow))
              :lock    (->LockCommand)
              :unlock-any (->UnlockAnyCommand)
              :apply   (->ApplyCommand)
              :destroy (->DestroyCommand))]
    (if cmd
      (execute cmd executor opts)
      (pp/pprint (into (sorted-map) opts)))))

(comment
  (->> (tofu {:args [:init :module-a :dev]
              :env :repl})
       (into (sorted-map)))

  (def custom-tofu-fn 
    (fn [args]
      (tofu (assoc args :command-registry 
                   {:destroy (->DestroyCommand)}))))
  )