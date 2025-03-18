(ns big-config.utils
  (:require
   [babashka.process :as process]
   [big-config :as bc]
   [big-config.msg :refer [step->message]]
   [bling.core :refer [bling]]
   [clojure.string :as str]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defmacro choice
  ([{:keys [on-success on-failure opts errmsg]}]
   `(let [exit# (::bc/exit ~opts)
          err# (::bc/err ~opts)
          msg# (if ~errmsg
                 ~errmsg
                 err#)]
      (if (= exit# 0)
        (recur ~on-success ~opts)
        (recur ~on-failure (assoc ~opts ::bc/err msg#))))))

(def default-opts {:continue true
                   :out :string
                   :err :string})

(defn handle-cmd [opts proc]
  (let [res (-> (select-keys proc [:exit :out :err :cmd])
                (update-vals (fn [v] (if (string? v)
                                       (str/replace v #"\x1B\[[0-9;]+m" "")
                                       v))))]

    (-> opts
        (update ::bc/procs (fnil conj []) res)
        (merge (-> res
                   (select-keys [:exit :err])
                   (update-keys (fn [k] (keyword "big-config" (name k)))))))))

(defn generic-cmd
  ([opts cmd]
   (let [proc (process/shell default-opts cmd)]
     (handle-cmd opts proc)))
  ([opts cmd key]
   (let [proc (process/shell default-opts cmd)]
     (-> opts
         (assoc key (-> (:out proc)
                        str/trim-newline))
         (handle-cmd proc)))))

(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(defn nested-sort-map [m]
  (cond
    (map? m) (into (sorted-map)
                   (for [[k v] m]
                     [k (cond
                          (map? v) (nested-sort-map v)
                          (vector? v) (mapv nested-sort-map v)
                          :else v)]))
    (vector? m) (mapv nested-sort-map m)
    :else m))

(defn git-push [opts]
  (generic-cmd opts "git push"))

(defn run-cmd [opts]
  (let [{:keys [::bc/env :big-config.run/run-cmd]} opts
        shell-opts {:continue true}
        shell-opts (case env
                     :shell shell-opts
                     :repl (merge shell-opts {:out :string
                                              :err :string}))
        proc (process/shell shell-opts run-cmd)]
    (handle-cmd opts proc)))

(defn opts->exit [opts]
  (let [{:keys [::bc/exit ::bc/env ::bc/err]} opts]
    (when (and (not= exit 0)
               (string? err))
      (binding [*out* *err*]
        (println (bling [:red.bold err]))))
    (case env
      :shell (exit-with-code exit)
      :repl opts)))

(defn default-step-fn [{:keys [f step opts]}]
  (let [opts (update opts ::bc/steps (fnil conj []) step)]
    (f opts)))

(defn step->workflow
  ([f single-step]
   (step->workflow f single-step nil))
  ([f single-step errmsg]
   (fn workflow
     ([opts]
      (workflow default-step-fn opts))
     ([step-fn opts]
      (let [{:keys [::bc/exit] :as opts} (step-fn {:f f
                                                   :step single-step
                                                   :opts opts})]
        (if (and errmsg (not= exit 0))
          (assoc opts ::bc/err errmsg)
          opts))))))

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
        (opts->exit new-opts))
      new-opts)))

(defn exit-step-fn
  [end {:keys [f step opts]}]
  (let [msg (step->message step)]
    (when msg
      (binding [*out* *err*]
        (println msg)))
    (let [new-opts (default-step-fn {:f f
                                     :step step
                                     :opts opts})]
      (when (= step end)
        (opts->exit new-opts))
      new-opts)))

(comment)
