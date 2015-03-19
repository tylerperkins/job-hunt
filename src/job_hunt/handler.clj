(ns job-hunt.handler
  (:require [compojure.core :refer :all]
            [compojure.route :as route]
            [ring.middleware.params :refer [wrap-params]]
            [clojure.edn :as edn]
            [job-hunt.model :as model]
            ))


(defroutes app-routes

  (GET "/" []
       "
<h1>Job Hunt</h1>

<p>This is an application for tracking the progress of background jobs.</p>

<ul>
<li>To register a new job with total 1000 and get its id, POST to <code>http://localhost:3000/job</code> with parameter <code>total=1000</code></li>
<li>To assign 500 to the progress of the job with id 9, PUT to <code>http://localhost:3000/job/9</code> with parameter <code>progress=_500</code></li>
<li>To add 100 to the progress of that job and get the resulting progress, PUT to <code>http://localhost:3000/job/9</code> with parameter <code>progress=100</code></li>
<li>To show all current jobs: <a href='http://localhost:3000/jobs'><code>http://localhost:3000/jobs</code></a></li>
<li>To show the job with id 9: <a href='http://localhost:3000/job/9'><code>http://localhost:3000/job/9</code></a></li>
</ul>
")

  ; Register a job. Renders as integer id of new job.
  (POST "/job" [total]
        (or (some-> (model/new-job (edn/read-string total)) str)
            {:status 400    ; Bad Request
             :content-type "text/plain"
             :body "The value for parameter \"total\" must be a positive integer."
             }))

  ; Render the list of all jobs in increasing order of id.
  (GET "/jobs" []
       (str (model/job-list)))

  ; Render a job by id.
  (GET "/job/:id" [id]
       (some-> (model/get-job (edn/read-string id))
               str))

  ; Delete a job by id. Renders id of deleted job.
  (DELETE "/job/:id" [id]
          (or (some-> (model/delete-job (edn/read-string id))
                      str)
              "\"Already deleted.\""))

  ; Update an existing job by id. Renders the new progress of updated job.
  (PUT "/job/:id" [id progress]
       (if-let [[_ underscore prog-str] (re-matches #"(_?)(-?\d+)" progress)
                ]
         ; Parameter progress matches a (maybe negative) integer, possibly
         ; prefixed by "_", indicating a replacement value, not increment.
         (some-> (apply model/update-job
                        (map edn/read-string [id prog-str underscore]))
                 str)

         ; Parmeter progress is neither like "123", nor "_123".
         {:status 400    ; Bad Request
          :content-type "text/plain"
          :body (str "The value for parameter \"progress\" must be a positive integer. "
                     "The value will be added to the existing progress, or prefix it "
                     "with \"_\" to replace the existing progress with the given value."
                )
          }))

  (route/not-found
    "\"Not Found.\""))


(def app
  (wrap-params app-routes))

