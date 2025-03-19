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
   [clojure.pprint :as pp]
   [big-config.tofu :as tofu]))

(defn ^:export tofu [{[cmd module profile] :args
                      env :env
                      step-fn :step-fn
                      aero-step-fn :aero-step-fn}]
  (let [step-fn (or step-fn exit-step-fn)
        aero-step-fn (or aero-step-fn exit-with-err-step-fn)
        read-module (step->workflow aero/read-module ::aero/read-module)
        opts {::run/cmd (name cmd)
              ::bc/env (or env :shell)
              ::aero/config "big-config.edn"
              ::aero/module module
              ::aero/profile profile}
        opts (read-module (partial aero-step-fn ::aero/read-module) opts)]
    (case cmd
      :opts             (pp/pprint (into (sorted-map) opts))
      (:init :plan)     (run/run (partial step-fn ::run/end) opts)
      :lock             (do (println (step->message ::rwl/lock-acquire))
                            (lock/lock (partial step-fn ::lock/end) opts))
      :unlock-any       (do (println (step->message ::rwl/lock-release-any-owner))
                            (unlock/unlock-any (partial step-fn ::lock/end) opts))
      (:apply :destroy) (rwl/run-with-lock (partial step-fn ::rwl/end) opts)
      :ci               (tofu/run-ci (partial step-fn ::tofu/end) opts))))

(comment
  (->> (tofu {:args [:init :module-a :dev]
              :env :repl})
       (into (sorted-map))))
