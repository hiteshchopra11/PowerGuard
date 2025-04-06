1. Is there a mechanism to collect usage data every 30 minutes?

There is an API to do this, API base URLis

@https://powerguardbackend.onrender.com/

Endpoint is `/api/analyze` (POST not GET),
For the sake of simplicaty of the hackathon while not losing praticality, you suggest me should I
use a button and fetch only that, or any other way to do it

Sample request body of API

{
"app_usage": [
{
"package_name": "string",
"app_name": "string",
"foreground_time_ms": 0,
"background_time_ms": 0,
"last_used": 0,
"launch_count": 0
}
],
"battery_stats": {
"level": 0,
"temperature": 0,
"is_charging": true,
"charging_type": "string",
"voltage": 0,
"health": "string",
"estimated_remaining_time": 0
},
"network_usage": {
"app_network_usage": [
{
"package_name": "string",
"data_usage_bytes": 0,
"wifi_usage_bytes": 0
}
],
"wifi_connected": true,
"mobile_data_connected": true,
"network_type": "string"
},
"wake_locks": [
{
"package_name": "string",
"wake_lock_name": "string",
"time_held_ms": 0
}
],
"device_id": "string",
"timestamp": "string"
}

Note that this is flexible and sample, know that you know what all is possible to be collected, you
smartly decide what l we can send in the API (it can be additional, which is not already mentioned
in the json as well), make sure the information should be useful and helpful in deciding actions for
the backend, think and let me know the updated jsonSample response json from API{
"actionable": [
{
"type": "string",
"app": "string",
"new_mode": "string",
"reason": "string",
"enabled": true
}
],
"summary": "string",
"usage_patterns": {
"additionalProp1": "string",
"additionalProp2": "string",
"additionalProp3": "string"
},
"timestamp": 0
}
Now that you know which all permissions are sorted, what all is possible to be done to save network
and/or battery. I want you to think of think with respect to our app, and list down few (maybe
5-10?) actionable that can be taken, example of few actionable -:1. Remove [app] from
background.2. Kill background network usage of [app]

3. Move this app to rarely used bucket.
4.

etc, note that what actions to be taken should not be decided in the android app, it is the
responsibility of the backend to do it, you just decide what all fixed set of actionable are
possible as well feasible and can can contribute to battery/network saving directly or indirectly
and list all actionable down for me. There can be function/executable code for each actionable so
maybe it can be used somewhere else too. Based not this decide the architecture, backend will make
sure send only those actionable which are listed under your decided actionable, There will a
mapping of these, so no new actionable will be sent by backend, unless it is added in the app.

Is there error handling for network issues during transmission?For the sake for hackathon, no need
of complex error handling, just make sure proper logs are there and app should never crash. Logs to
debug when things went south.

* Is there authentication/security for API communication?NO there is no , simple POST API with
  request body and responseDo we have a clear mapping between backend actionable and concrete app
  functions?Ideally we will, just that actionable were not decided yet, you can do it for me as
  mentioned by me earlier, and finally we will reach a state there will be clear mapping, and no
  alien actionable will be there.
* Is there a parser for the received actionable?Not yet, but please create one after choosing the
  actionable, suggest me a sample backend actionable format how you wanna receive, (
  strings/integers etc), do what is best according to you architecturally.
* Is there prioritization logic if multiple actionable are received?Good question, no need of this.
  Execute all actionable one by one assuming all have the same priority (as the time to execute
  actionable will be minimum)
* Are the default rules for new users implemented?Good question. There will be default actionable
  at the backend. You decide that and let me know, but note that this will not be part of android
  app. There will be default actionable on the backend, for example if there is no battery related
  usage patterns, the default battery saving optimization will be enabled battery saver at 15%,
  default actionable will then be kill all apps in the background other than important apps like
  messaging apps. You think about it and give me default usage_atterns as well as action
* Is there a mechanism to transition from default rules to AI-recommended rules?AI will just decide
  what rules are, but rules as discussed earlier will be pre defined and only particular rules will
  be returned.
* Is there a widget to display the non-technical summary?Yes please create a widget, meanwhile while
  the data is being processed by our backend, showing some loading state like, please wait while we
  optimize your network/battery. Once data is ready, show the summary, I will make sure that backend
  sends summary short and long enough to fit in a 4*4 widget. Upon clicking the widget, our app
  should open, where all previous along with human readable date time should be shown, will refine
  the UI later.
* Does the app have a way to track and display historical actions taken?Yes as mentioned in earlier
  point, show the historical human readable actions in descending order of time. If possible,
  segregate the sections as per date/hour etc, you decide the ui/ux but try to make it appealing.
* How do we verify the end-to-end flow works correctly?You think and decide a mechanism, for maybe
  create bash scripts will predefined raw json for user activity, and I will do the same for testing
  purpose from backend where there will be some pre defined dummy usage patterns. Think and decide
  the testing mechanism of it, I gave you the idea.
* Are there mechanisms to validate that actionable are being applied correctly?Add logging or maybe
  try catch for it by making it verbose? Maybe such that I apply I tag and know if the status of the
  action was met or not. If possible, write UI/unit tests for the same or automation script?
