cognition-caps
==============
[![Build Status](https://travis-ci.org/lhanson/cognition-caps.png)](https://travis-ci.org/lhanson/cogniton-caps)

This is the software which runs the
[Cognition Caps website](http://www.wearcognition.com).

It's written in Clojure because it's awesome and I'm silly like that.

Setup
=====
For Heroku deployment, we need to set the database credentials
[in environment variables](http://devcenter.heroku.com/articles/config-vars#local-setup).
For local development and migration work against other databases we'll read
settings from a local file, so rename datasource.properties.example and enter
the credentials there.

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
