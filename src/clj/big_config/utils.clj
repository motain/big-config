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

(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

(defn choice [{:keys [on-success
                      on-failure
                      errmsg
                      opts]}]
  (let [exit (::bc/exit opts)
        err (::bc/err opts)
        msg (if errmsg
              errmsg
              err)]
    (if (= exit 0)
      [on-success opts]
      [on-failure (assoc opts ::bc/err msg)])))

(defn default-step-fn [{:keys [f step opts]}]
  (let [opts (update opts ::bc/steps (fnil conj []) step)]
    (f opts)))

(defn ->workflow
  [{:keys [first-step
           step-fn
           wire-fn
           next-fn]}]
  (fn workflow
    ([opts]
     (workflow (or step-fn default-step-fn) opts))
    ([step-fn opts]
     (loop [step first-step
            opts opts]
       (let [[f next-step errmsg] (wire-fn step step-fn)
             opts (try (step-fn {:f f
                                 :step step
                                 :opts opts})
                       (catch Exception e
                         (-> (if-let [ex-opts (ex-data e)]
                               ex-opts
                               opts)
                             (merge {::bc/err (ex-message e)
                                     ::bc/exit 1}))))
             next-fn (if (keyword? next-fn)
                       (fn [_ next-step opts]
                         (if next-step
                           (choice {:on-success next-step
                                    :on-failure next-fn
                                    :errmsg errmsg
                                    :opts opts})
                           [nil opts]))
                       next-fn)
             [next-step next-opts] (next-fn step next-step opts)]
         (if next-step
           (recur next-step next-opts)
           next-opts))))))

#_(->> ((->workflow {:first-step ::foo
                     :wire-fn (fn [step _]
                                (case step
                                  ::foo [#(throw (Exception. "Java exception") #_%) ::end]
                                  ::end [identity]))
                     :next-fn ::end}) {::bar :baz})
       (into (sorted-map)))

#_(->> ((->workflow {:first-step ::foo
                     :wire-fn (fn [step _]
                                (case step
                                  ::foo [#(throw (ex-info "Error" %)) ::end]
                                  ::end [identity]))
                     :next-fn ::end})
        {::bar :baz})
       (into (sorted-map)))

#_(try (throw (Exception. "Java exception"))
       (catch Exception e
         ((juxt ex-message ex-data) e)))

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
