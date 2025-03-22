(ns big-config.main
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.core :refer [exit-step-fn exit-with-err-step-fn step->workflow]]
   [big-config.lock :as lock]
   [big-config.msg :refer [step->message]]
   [big-config.run :as run]
   [big-config.run-with-lock :as rwl]
   [big-config.unlock :as unlock]
   [clojure.pprint :as pp]))

(defn throw? [{:keys [::bc/exit] :as opts}]
  (when (not= exit 0)
    (throw (ex-info "Failure in the aero/read-module" opts)))
  opts)

(defn print-step-fn [f step opts]
  (let [msg (step->message step)]
    (when msg
      (binding [*out* *err*]
        (println msg))))
  (f step opts))

(defn render-opts [{:keys [step-fns env module profile]} [cmd run-cmd]]
  (let [step-fns (concat step-fns [(partial exit-with-err-step-fn ::aero/start)])
        read-module (step->workflow aero/read-module ::aero/start)
        opts {::run/cmd (name cmd)
              ::bc/env (or env :shell)
              ::aero/config "big-config.edn"
              ::aero/module module
              ::aero/profile profile}
        opts (if run-cmd
               (assoc opts ::run/run-cmd run-cmd)
               opts)
        opts (read-module step-fns opts)]
    (throw? opts)))

(defn ^:export tofu [{[cmd module profile] :args
                      env :env
                      step-fns :step-fns
                      aero-step-fns :aero-step-fns}]
  (let [step-fns (concat step-fns [(case cmd
                                     :opts             nil
                                     (:init :plan)     (partial exit-step-fn (run/run))
                                     :lock             (partial exit-step-fn (lock/lock))
                                     :unlock-any       (partial exit-step-fn (unlock/unlock-any))
                                     (:apply :destroy) (partial exit-step-fn (rwl/run-with-lock))
                                     (:ci)             (partial exit-step-fn (unlock/unlock-any)))])
        render-opts (partial render-opts {:step-fns aero-step-fns
                                          :env env
                                          :module module
                                          :profile profile})
        opts (render-opts [cmd])
        run-cmd [:big-config.aero/join
                 "bash -c 'cd "
                 :big-config.run/working-dir
                 " && direnv exec . tofu "
                 :big-config.run/cmd " -auto-approve'"]]
    (case cmd
      :opts             (pp/pprint (into (sorted-map) opts))
      (:init :plan)     (run/run step-fns opts)
      :lock             (do (println (step->message ::rwl/lock-acquire))
                            (lock/lock step-fns opts))
      :unlock-any       (do (println (step->message ::rwl/lock-release-any-owner))
                            (unlock/unlock-any step-fns opts))
      (:apply :destroy) (rwl/run-with-lock step-fns opts)
      (:ci)             (do (println (step->message ::rwl/lock-acquire))
                            (lock/lock opts)
                            (run/run [print-step-fn] (render-opts [:init]))
                            (run/run [print-step-fn] (render-opts [:apply run-cmd]))
                            (run/run [print-step-fn] (render-opts [:destroy run-cmd]))
                            (println (step->message ::rwl/lock-release-any-owner))
                            (unlock/unlock-any step-fns opts)))))

(comment
  (->> (tofu {:args [:ci :module-a :dev]
              :env :repl})
       (into (sorted-map))))
