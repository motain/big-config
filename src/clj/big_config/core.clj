(ns big-config.core
  (:require
   [babashka.process :as process]
   [big-config :as bc]
   [clojure.string :as str]))

(defn choice [{:keys [on-success
                      on-failure
                      opts]}]
  (let [exit (::bc/exit opts)]
    (if (= exit 0)
      [on-success opts]
      [on-failure opts])))

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
                     ::bc/exit 1
                     ::bc/stack-trace (apply str (interpose "\n" (map str (.getStackTrace e))))})))))

(defn resolve-next-fn [next-fn last-step]
  (if (nil? next-fn)
    (fn [_ next-step opts]
      (if next-step
        (choice {:on-success next-step
                 :on-failure last-step
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
           (let [[f next-step] (wire-fn step step-fns)
                 f (compose step-fns f)
                 opts (try-f f step opts)
                 next-fn (resolve-next-fn next-fn last-step)
                 [next-step next-opts] (next-fn step next-step opts)]
             (if next-step
               (recur next-step next-opts)
               next-opts))))))))

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

(comment)
