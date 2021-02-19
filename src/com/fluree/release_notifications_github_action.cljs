(ns com.fluree.release-notifications-github-action
  (:require ["@actions/core" :as actions]
            ["@octokit/core" :refer [Octokit]]
            [clojure.string :as str]
            [cljs.core.async :refer [go]]
            [cljs.core.async.interop :refer-macros [<p!]]
            [oops.core :refer [oget oget+]]
            [cljs-node-io.core :refer [slurp]]))

(defn parse-repo [repo]
  (let [[owner repo] (str/split repo #"/")]
    {:owner owner, :repo repo}))

(defn get-env [v]
  (oget+ (.-env js/process) v))

(defn this-repo []
  (-> "GITHUB_REPOSITORY"
      get-env
      parse-repo))

(defn canonical-repo [repo]
  (if (str/includes? repo "/")
    (parse-repo repo)
    (assoc (this-repo) :repo repo)))

(defn event-type []
  (let [{:keys [owner repo]} (this-repo)]
    (str owner "-" repo "-release")))

(defn new-octokit [access-token]
  (Octokit. #js {:auth access-token}))

(def octokit
  (-> "token"
      actions/getInput
      new-octokit
      delay))

(defn client-payload []
  (->> "GITHUB_EVENT_PATH"
       get-env
       slurp
       (.parse js/JSON)))

(defn dispatch-event [{:keys [owner repo] :as full-repo}]
  (let [payload (client-payload)
        body #js {:owner          owner
                  :repo           repo
                  :event_type     (event-type)
                  :client_payload payload}]
    (println "Dispatch body:" (pr-str body))
    (.request @octokit "POST /repos/{owner}/{repo}/dispatches" body)))

(defn notify-repo [repo]
  (println "Notifying repo:" repo)
  (go
    (let [resp (<p! (dispatch-event repo))]
      (when-not (= 204 (oget resp "status"))
        (actions/setFailed (str "Repository dispatch event response was" (pr-str resp)))))))

(defn run [repos]
  (println "Notifying" (count repos) "repos")
  (run! (comp notify-repo canonical-repo) repos))

(try
  (let [repos (->> "repos-to-notify" actions/getInput (.parse js/JSON))]
    (run repos))
  (catch js/Error e
    (actions/setFailed (.-message e))))
