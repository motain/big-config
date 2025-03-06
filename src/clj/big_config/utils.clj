(ns big-config.utils
  (:require
   [babashka.process :as process]
   [clojure.string :as str]
   [com.bunimo.clansi :as clansi]))

(defn exit-with-code [n]
  (shutdown-agents)
  (flush)
  (System/exit n))

(defn handle-last-cmd [opts]
  (let [{:keys [cmd-results]} opts]
    (last cmd-results)))

(defmacro recur-ok-or-end
  ([key opts]
   `(recur-ok-or-end ~key ~opts nil))
  ([key opts msg]
   `(let [exit# (:exit ~opts)
          err# (:err ~opts)
          msg# (if ~msg
                 ~msg
                 err#)]
      (if (= exit# 0)
        (recur ~key ~opts)
        (recur :end (assoc ~opts :err msg#))))))

(defmacro recur-not-ok-or-end [key opts]
  `(let [proc# (handle-last-cmd ~opts)
         exit# (get proc# :exit)]
     (if (= exit# 0)
       (recur :end ~opts)
       (recur ~key ~opts))))

(def default-opts {:continue true
                   :out :string
                   :err :string})

(defn handle-cmd [opts proc]
  (let [{:keys [exit err]} proc
        res (select-keys proc [:exit :out :err :cmd])]
    (-> opts
        (update :cmd-results (fnil conj []) res)
        (merge {:exit exit :err err}))))

(defn generic-cmd-v2
  ([opts cmd]
   (let [proc (process/shell default-opts cmd)]
     (handle-cmd opts proc)))
  ([opts cmd key]
   (let [proc (process/shell default-opts cmd)]
     (-> opts
         (assoc key (-> (:out proc)
                        str/trim-newline))
         (handle-cmd proc)))))

(defn generic-cmd
  ([opts cmd]
   (let [res (process/shell default-opts cmd)]
     (update opts :cmd-results (fnil conj []) res)))
  ([opts cmd key]
   (let [res (process/shell default-opts cmd)]
     (-> opts
         (assoc key (-> (:out res)
                        str/trim-newline))
         (update :cmd-results (fnil conj []) res)))))

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

(defn description-for-step [step]
  (let [messages {:check-tag "check-tag"
                  :git-push "Pushing the changes to git"
                  :git-diff "git-diff"
                  :git-check "Checking if there are files not in the index"
                  :get-remote-tag "get-remote-tag"
                  :compare-revisions "compare-revisions"
                  :upstream-name "upstream-name"
                  :lock-release "Releasing lock"
                  :generate-lock-id "generate-lock-id"
                  :push-tag "push-tag"
                  :current-revision "current-revision"
                  :run-cmd "Running the command with the lock"
                  :origin-revision "origin-revision"
                  :fetch-origin "fetch-origin"
                  :delete-tag "delete-tag"
                  :lock-acquire "Acquiring lock"
                  :prev-revision "prev-revision"
                  :read-tag "read-tag"
                  :create-tag "create-tag"
                  :delete-remote-tag "delete-remote-tag"}]
    (as-> step $
      (get messages $)
      (clansi/style $ :green))))

(defn error-for-step [step]
  (let [messages {:check-tag "check-tag"
                  :compare-revisions "compare-revisions"
                  :create-tag "create-tag"
                  :current-revision "current-revision"
                  :delete-remote-tag "delete-remote-tag"
                  :delete-tag "delete-tag"
                  :fetch-origin "fetch-origin"
                  :generate-lock-id "generate-lock-id"
                  :get-remote-tag "get-remote-tag"
                  :git-push "Pushing the changes to git"
                  :git-diff "The working directory is not clean"
                  :git-check "The working directory is not clean"
                  :lock-acquire "Failed to acquire lock"
                  :lock-release "Releasing lock"
                  :origin-revision "origin-revision"
                  :prev-revision "prev-revision"
                  :push-tag "push-tag"
                  :run-cmd "The command executed with the lock failed"
                  :read-tag "read-tag"
                  :upstream-name "upstream-name"}]
    (as-> step $
      (get messages $)
      (clansi/style $ :red))))

(comment)
