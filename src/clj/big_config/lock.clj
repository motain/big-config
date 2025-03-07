(ns big-config.lock
  (:require
   [babashka.process :as process]
   [big-config.utils :as utils :refer [generic-cmd handle-cmd
                                       nested-sort-map recur-not-ok-or-end
                                       recur-ok-or-end]]
   [buddy.core.codecs :as codecs]
   [buddy.core.hash :as hash]
   [clojure.edn :as edn]
   [clojure.string :as str]))

(defn generate-lock-id [opts]
  (let [{:keys [lock-keys owner]} opts
        lock-details (select-keys opts lock-keys)
        lock-name (-> lock-details
                      nested-sort-map
                      pr-str
                      hash/sha256
                      codecs/bytes->hex
                      str/upper-case
                      (subs 0 4)
                      (as-> $ (str "LOCK-" $)))]
    (-> opts
        (assoc :lock-details lock-details)
        (update :lock-details assoc :owner owner)
        (merge {:lock-name lock-name
                :exit 0
                :err nil}))))

(defn delete-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git tag -d %s" lock-name))))

(defn create-tag [opts]
  (let [{:keys [lock-name
                lock-details]} opts
        proc (-> (process/shell {:continue true
                                 :in (pr-str lock-details)
                                 :out :string
                                 :err :string} (format "git tag -a %s -F -" lock-name)))]
    (handle-cmd opts proc)))

(defn push-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git push origin %s" lock-name))))

(defn delete-remote-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git push --delete origin %s" lock-name))))

(defn get-remote-tag [opts]
  (let [{:keys [lock-name]} opts]
    (generic-cmd opts (format "git fetch origin tag %s --no-tags" lock-name))))

(defn read-tag [opts]
  (let [{:keys [lock-name]} opts
        cmd (format "git cat-file -p %s" lock-name)]
    (generic-cmd opts cmd :tag-content)))

(defn parse-tag-content [tag-content]
  (->> tag-content
       str/split-lines
       (filter #(str/starts-with? % "{"))
       first
       edn/read-string))

(defn check-tag [opts]
  (let [{:keys [tag-content]} opts
        ownership (every? (fn [[k v]]
                            (= (get opts k) v))
                          (parse-tag-content tag-content))]
    (merge opts (if ownership
                  {:exit 0
                   :err nil}
                  {:exit 1
                   :err "Different owner"}))))

(defn acquire
  ([opts]
   (acquire opts identity))
  ([opts end-fn]
   (acquire opts end-fn (fn [_])))
  ([opts end-fn step-fn]
   (loop [step :generate-lock-id
          opts opts]
     (step-fn step)
     (let [opts (update opts :steps (fnil conj []) step)]
       (case step
         :generate-lock-id (as-> (generate-lock-id opts) $
                             (recur-ok-or-end :delete-tag $))
         :delete-tag (as-> (delete-tag opts) $
                       (recur :create-tag $))
         :create-tag (as-> (create-tag opts) $
                       (recur-ok-or-end :push-tag $))
         :push-tag (as-> (push-tag opts) $
                     (recur-not-ok-or-end :get-remote-tag $))
         :get-remote-tag (as-> (-> opts
                                   delete-tag
                                   get-remote-tag) $
                           (recur-ok-or-end :read-tag $))
         :read-tag (as-> (read-tag opts) $
                     (recur-ok-or-end :check-tag $))
         :check-tag (as-> (check-tag opts) $
                      (recur :end $))
         :end (end-fn opts))))))

(defn release-any-owner
  ([opts]
   (release-any-owner opts identity))
  ([opts end-fn]
   (release-any-owner opts end-fn (fn [_])))
  ([opts end-fn step-fn]
   (loop [step :generate-lock-id
          opts opts]
     (step-fn step)
     (let [opts (update opts :steps (fnil conj []) step)]
       (case step
         :generate-lock-id (as-> (generate-lock-id opts) $
                             (recur :delete-tag $))
         :delete-tag (as-> (delete-tag opts) $
                       (recur :delete-remote-tag $))
         :delete-remote-tag (as-> (delete-remote-tag opts) $
                              (recur :end $))
         :end (end-fn opts))))))

(comment)
