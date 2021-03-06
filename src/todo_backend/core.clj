(ns todo-backend.core
  (:require reitit.coercion.schema
            [reitit.ring :as ring]
            [reitit.ring.coercion :as rrc]
            [ring.adapter.jetty :as jetty]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.json :refer [wrap-json-body wrap-json-response]]
            [ring.middleware.keyword-params :refer [wrap-keyword-params]]
            [schema.core :as s]
            [todo-backend.store :as store]))

(defn ok [body]
  {:status 200
   :body body})

(defn append-todo-url [todo request]
  (let [host (-> request :headers (get "host" "localhost"))
        scheme (name (:scheme request))
        id (:id todo)]
    (merge todo {:url (str scheme "://" host "/todos/" id)})))

(def app-routes
  (ring/ring-handler
   (ring/router
    [["/todos" {:get (fn [req] (ok (map #(append-todo-url % req) (store/get-all-todos))))
                :post (fn [{:keys [body] :as req}] (-> body
                                                      store/create-todos
                                                      (#(append-todo-url % req))
                                                      ok))
                :delete (fn [_] (store/delete-all-todos)
                          {:status 204})
                :options (fn [_] {:status 200})}]
     ["/todos/:id" {:parameters {:path {:id s/Int}}
                    :get (fn [{:keys [parameters] :as req}] (-> (store/get-todo (-> parameters :path :id))
                                                               (#(append-todo-url % req))
                                                               ok))
                    :patch (fn [{:keys [parameters body] :as req}] (-> body
                                                                      (#(store/update-todo (-> parameters :path :id) %))
                                                                      (#(append-todo-url % req))
                                                                      ok))
                    :delete (fn [{:keys [parameters]}] (store/delete-todos (-> parameters :path :id))
                              {:status 204})}]]
    {:data {:coercion reitit.coercion.schema/coercion
            :middleware [rrc/coerce-response-middleware
                         rrc/coerce-request-middleware
                         rrc/coerce-exceptions-middleware
                         wrap-keyword-params
                         wrap-json-response
                         [wrap-json-body {:keywords? true}]
                         [wrap-cors :access-control-allow-origin [#".*"]
                                    :access-control-allow-methods [:get :put :post :patch :delete]]]}})

   (ring/create-default-handler
    {:not-found (constantly {:status 404 :body "Not found"})})))

(defn -main [port]
  (jetty/run-jetty #'app-routes {:port (Integer. port)
                                 :join? false}))

(comment
  (def server (jetty/run-jetty #'app-routes {:port 3000
                                             :join? false})))
