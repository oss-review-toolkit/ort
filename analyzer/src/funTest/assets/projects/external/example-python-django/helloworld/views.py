import os
import subprocess

from django.http import HttpResponse


def index(request):
    whom = os.getenv('POWERED_BY', 'Deis')
    release = os.getenv('WORKFLOW_RELEASE', 'unknown')
    container = subprocess.getoutput('hostname').strip()
    return HttpResponse('Powered by {whom}\nRelease {release} on {container}'.format(**locals()))


# Return 200 for kubernetes healthcheck.
def healthz(request):
    return HttpResponse('')
