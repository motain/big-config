(ns big-config.core
  (:require
   [babashka.process :as process]
   [big-config :as bc]
   [big-config.msg :refer [step->message]]
   [bling.core :refer [bling]]
   [clojure.string :as str]))

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

(defn compose [step-fns f]
  (reduce (fn [f-acc f-next]
            (partial f-next f-acc)) (fn [_ opts] (f opts)) step-fns))

(defn resolve-step-fns [step-fns]
  (-> (map (fn [f] (cond
                     (fn? f) f
                     (string? f) (-> f symbol requiring-resolve)
                     :else (throw (ex-info "f is neither a string nor a fn" {:f f}))))
           step-fns)
      reverse))

(defn try-f [f step opts]
  (try (f step opts)
       (catch Exception e
         (-> (if-let [ex-opts (ex-data e)]
               ex-opts
               opts)
             (merge {::bc/err (ex-message e)
                     ::bc/exit 1})))))

(defn resolve-next-fn [next-fn last-step errmsg]
  (if (nil? next-fn)
    (fn [_ next-step opts]
      (if next-step
        (choice {:on-success next-step
                 :on-failure last-step
                 :errmsg errmsg
                 :opts opts})
        [nil opts]))
    next-fn))

(defn ->workflow
  [{:keys [first-step
           last-step
           step-fns
           wire-fn
           next-fn]}]
  (let [last-step (or last-step
                      (keyword (namespace first-step) "end"))]
    (fn workflow
      ([]
       [first-step last-step])
      ([opts]
       (workflow (or step-fns []) opts))
      ([step-fns opts]
       (let [step-fns (resolve-step-fns step-fns)]
         (loop [step first-step
                opts opts]
           (let [[f next-step errmsg] (wire-fn step step-fns)
                 f (compose step-fns f)
                 opts (try-f f step opts)
                 next-fn (resolve-next-fn next-fn last-step errmsg)
                 [next-step next-opts] (next-fn step next-step opts)]
             (if next-step
               (recur next-step next-opts)
               next-opts))))))))

(defn step->workflow
  ([f single-step]
   (step->workflow f single-step nil))
  ([f single-step errmsg]
   (let [end-step (keyword (namespace single-step) "end")]
     (->workflow {:first-step single-step
                  :wire-fn (fn [step _]
                             (cond
                               (= step single-step) [f end-step errmsg]
                               (= step end-step) [identity]))}))))

(defn deep-merge
  "Recursively merges maps."
  [& maps]
  (letfn [(m [& xs]
            (if (some #(and (map? %) (not (record? %))) xs)
              (apply merge-with m xs)
              (last xs)))]
    (reduce m maps)))

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

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defn opts->exit [opts]
  (let [{:keys [::bc/exit ::bc/env ::bc/err]} opts]
    (when (and (not= exit 0)
               (string? err))
      (binding [*out* *err*]
        (println (bling [:red.bold err]))))
    (case env
      :shell (exit-with-code exit)
      :repl opts)))

(defn exit-with-err-step-fn
  [end f step opts]
  (let [msg (step->message step)]
    (when msg
      (binding [*out* *err*]
        (println msg)))
    (let [new-opts (f step opts)
          {:keys [::bc/exit]} new-opts]
      (if (and (= step end) (not= exit 0))
        (opts->exit new-opts)
        new-opts))))

(defn exit-step-fn
  [end f step opts]
  (let [msg (step->message step)]
    (when msg
      (binding [*out* *err*]
        (println msg)))
    (let [new-opts (f step opts)]
      (if (= step end)
        (opts->exit new-opts)
        new-opts))))

(comment)
