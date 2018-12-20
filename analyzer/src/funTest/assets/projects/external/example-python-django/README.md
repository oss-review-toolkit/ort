# Python Quick Start Guide

This guide will walk you through deploying a Python / [Django](https://www.djangoproject.com/) application on [Deis Workflow][].

## Usage

```console
$ git clone https://github.com/deis/example-python-django.git
$ cd example-python-django
$ deis create
Creating Application... done, created italic-rucksack
Git remote deis added
remote available at ssh://git@deis-builder.deis.rocks:2222/italic-rucksack.git
$ git push deis master
Counting objects: 75, done.
Delta compression using up to 4 threads.
Compressing objects: 100% (46/46), done.
Writing objects: 100% (75/75), 12.01 KiB | 0 bytes/s, done.
Total 75 (delta 31), reused 53 (delta 21)
Starting build... but first, coffee!
-----> Python app detected
-----> Installing python-3.5.2
       $ pip install -r requirements.txt
       Collecting Django==1.10.0 (from -r requirements.txt (line 1))
       Downloading Django-1.10-py2.py3-none-any.whl (6.8MB)
       Collecting gunicorn==19.6.0 (from -r requirements.txt (line 2))
       Downloading gunicorn-19.6.0-py2.py3-none-any.whl (114kB)
       Installing collected packages: Django, gunicorn
       Successfully installed Django-1.10.0 gunicorn-19.6.0

       $ python manage.py collectstatic --noinput
       56 static files copied to '/app/staticfiles'.

-----> Discovering process types
       Procfile declares types -> web
-----> Compiled slug size is 61M
Build complete.
Launching App...
Done, italic-rucksack:v2 deployed to Workflow

Use 'deis open' to view this application in your browser

To learn more, use 'deis help' or visit https://deis.com/

To ssh://git@deis-builder.deis.rocks:2222/italic-rucksack.git
 * [new branch]      master -> master
$ curl http://italic-rucksack.deis.rocks
Powered by Deis
Release v2 on italic-rucksack-web-1653109058-vjlh0
```

## Additional Resources

* [GitHub Project](https://github.com/deis/workflow)
* [Documentation](https://deis.com/docs/workflow/)
* [Blog](https://deis.com/blog/)

[Deis Workflow]: https://github.com/deis/workflow#readme
