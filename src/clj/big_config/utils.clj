(ns big-config.utils
  (:require
   [babashka.process :as process]
   [big-config.run :as run]
   [clojure.string :as str]
   [com.bunimo.clansi :as clansi :refer [style]]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defmacro recur!
  ([key end opts]
   `(recur! ~key ~end ~opts nil))
  ([key end opts msg]
   `(let [exit# (:exit ~opts)
          err# (:err ~opts)
          msg# (if ~msg
                 ~msg
                 err#)]
      (if (= exit# 0)
        (recur ~key ~opts)
        (recur ~end (assoc ~opts :err msg#))))))

(defmacro not-recur! [key end opts]
  `(let [exit# (:exit ~opts)]
     (if (= exit# 0)
       (recur ~end ~opts)
       (recur ~key ~opts))))

(def default-opts {:continue true
                   :out :string
                   :err :string})

(defn handle-cmd [opts proc]
  (let [res (-> (select-keys proc [:exit :out :err :cmd])
                (update-vals (fn [v] (if (string? v)
                                       (str/replace v #"\x1B\[[0-9;]+m" "")
                                       v))))]
    (-> opts
        (update :cmd-results (fnil conj []) res)
        (merge (select-keys res [:exit :err])))))

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

(defn step->message [step]
  (let [messages {::run/generate-main-tf-json "Generating the main.tf.json file"
                  :lock-release-any-owner "Releasing lock"
                  :git-check "Checking if there are files not in the index"
                  ::run/run-cmd "Running the tofu command"
                  :git-push "Pushing the changes to git"
                  :lock-acquire "Acquiring lock"}]
    (as-> step $
      (get messages $)
      (clansi/style $ :green))))

(defn println-step-fn
  ([step]
   (println-step-fn step nil))
  ([step _opts]
   (when (not= :end step)
     (println (step->message step)))))

(defn run-cmd [opts]
  (let [{:keys [env run-cmd]} opts
        shell-opts {:continue true}
        shell-opts (case env
                     :shell shell-opts
                     :repl (merge shell-opts {:out :string
                                              :err :string}))
        proc (process/shell shell-opts run-cmd)]
    (handle-cmd opts proc)))

(defn exit-end-fn
  ([opts]
   (exit-end-fn nil opts))
  ([err-msg opts]
   (let [{:keys [exit env err]} opts
         err (or err-msg err)]
     (when-not (= exit 0)
       (println (style err :red)))
     (case env
       :shell (exit-with-code exit)
       :repl opts))))

(defn default-step-fn [{:keys [f step opts]}]
  (let [opts (update opts :steps (fnil conj []) step)]
    (f opts)))

(comment)
