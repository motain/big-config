(ns big-config.main
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.lock :as lock]
   [big-config.msg :refer [step->message]]
   [big-config.run :as run]
   [big-config.run-with-lock :as rwl]
   [big-config.unlock :as unlock]
   [big-config.utils :refer [exit-step-fn exit-with-err-step-fn step->workflow]]
   [clojure.pprint :as pp]))

(defn ^:export tofu [{[cmd module profile] :args
                      env :env
                      step-fns :step-fns
                      aero-step-fns :aero-step-fns}]
  (let [step-fns (or step-fns [(case cmd
                                 :opts             nil
                                 (:init :plan)     (partial exit-step-fn ::run/end)
                                 :lock             (partial exit-step-fn ::lock/end)
                                 :unlock-any       (partial exit-step-fn ::lock/end)
                                 (:apply :destroy) (partial exit-step-fn ::rwl/end))])
        aero-step-fns (or aero-step-fns [(partial exit-with-err-step-fn ::aero/start)])
        read-module (step->workflow aero/read-module ::aero/start)
        opts {::run/cmd (name cmd)
              ::bc/env (or env :shell)
              ::aero/config "big-config.edn"
              ::aero/module module
              ::aero/profile profile}
        opts (read-module aero-step-fns opts)]
    (case cmd
      :opts             (pp/pprint (into (sorted-map) opts))
      (:init :plan)     (run/run step-fns opts)
      :lock             (do (println (step->message ::rwl/lock-acquire))
                            (lock/lock step-fns opts))
      :unlock-any       (do (println (step->message ::rwl/lock-release-any-owner))
                            (unlock/unlock-any step-fns opts))
      (:apply :destroy) (rwl/run-with-lock step-fns opts))))

(comment
  (->> (tofu {:args [:init :module-a :dev]
              :env :repl})
       (into (sorted-map))))
