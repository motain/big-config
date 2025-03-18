(ns big-config.main
  (:require
   [big-config :as bc]
   [big-config.aero :as aero]
   [big-config.lock :as lock]
   [big-config.msg :refer [step->message]]
   [big-config.run :as run]
   [big-config.run-with-lock :as rwl]
   [big-config.unlock :as unlock]
   [big-config.utils :refer [default-step-fn exit-end-fn step->workflow]]))

(defn exit-with-err-step-fn
  [end {:keys [f step opts]}]
  (let [msg (step->message step)]
    (when msg
      (binding [*out* *err*]
        (println msg)))
    (let [new-opts (default-step-fn {:f f
                                     :step step
                                     :opts opts})
          {:keys [::bc/exit]} new-opts]
      (when (and (= step end) (not= exit 0))
        (exit-end-fn new-opts))
      new-opts)))

(defn run-step-fn
  [end {:keys [f step opts]}]
  (let [msg (step->message step)]
    (when msg
      (binding [*out* *err*]
        (println msg)))
    (let [new-opts (default-step-fn {:f f
                                     :step step
                                     :opts opts})]
      (when (= step end)
        (exit-end-fn new-opts))
      new-opts)))

(defn ^:export tofu [{[cmd module profile] :args
                      env :env
                      step-fn :step-fn}]
  (let [step-fn (or step-fn run-step-fn)
        read-module (step->workflow aero/read-module ::aero/read-module)
        opts {::run/cmd (name cmd)
              ::bc/env (or env :shell)
              ::aero/config "big-config.edn"
              ::aero/module module
              ::aero/profile profile}
        opts (read-module (partial exit-with-err-step-fn ::aero/read-module) opts)]
    (case cmd
      (:init :plan)     (run/run (partial step-fn ::run/end) opts)
      :lock             (lock/lock (partial step-fn ::lock/end) opts)
      :unlock-any       (unlock/unlock-any (partial step-fn ::lock/end) opts)
      (:apply :destroy) (rwl/run-with-lock (partial step-fn ::rwl/end) opts))))

(comment
  (->> (tofu {:args [:init :module-a :dev]
              :env :repl})
       (into (sorted-map))))
