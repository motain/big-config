(ns big-config.step-fns
  (:require
   [big-config :as bc]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defn exit-step-fn [end f step opts]
  (let [{:keys [::bc/env ::bc/exit] :as opts} (f step opts)]
    (if (= step end)
      (case env
        :shell (exit-with-code exit)
        :repl opts)
      opts)))

(defn tap-step-fn [f step opts]
  (tap> [step :before opts])
  (let [opts (f step opts)]
    (tap> [step :after opts])
    opts))
