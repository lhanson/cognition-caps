cognition-caps
==============

This is the software which runs the
[Cognition Caps website](http://www.wearcognition.com). Or, it will soon.

It's written in Clojure because it's awesome and I'm silly like that.

Setup
=====
For Heroku deployment, we need to set the database credentials
[in environment variables](http://devcenter.heroku.com/articles/config-vars#local-setup).
For local development and migration work against other databases we'll read
settings from a local file, so rename datasource.properties.example and enter
the credentials there.

TODO
====
Because a good project is never finished.

* Write a database migration from ExpressionEngine/MySQL into SimpleDb.
* Serve CSS and JS from S3 from either a gzipped or non-gzipped bucket depending
  on application-detected content negotiation
* Use Sass for CSS
* Canonicalize on no-www URL subdomain
* Maybe have each cap image's background switch to a highlighted green version on hover
* Make sure Jetty is returning Content-Type headers
* Add a history tracking entity. When a cap attribute is changed, store a
  diff into a History domain; something like
  {:type cap :changed-field "price" :old-value "25" :date-changed "2011-02-01"}.
  The same could apply to other items as well.
* Map all old URLs into the new application.
  - Include a series of old url-titles as the title can change multiple times
  - If/when an item is renamed, it cannot be renamed to a name that a different
    item has owned in the past so 301 redirects converge on the unique items
* Implement all recommendations given by Chrome's PageSpeed extension.
* Extract annotate-ordered-values and merge-large-descriptions from simpledb
  code to be generic wrappers around rummage.

License
=======

Copyright 2011 Lyle Hanson

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

[http://www.apache.org/licenses/LICENSE-2.0](http://www.apache.org/licenses/LICENSE-2.0)

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
