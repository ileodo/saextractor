__author__ = 'LeoDong'
from django.conf.urls import url
from . import views

urlpatterns = [
    url(r'^$', views.index, name='index'),
    url(r'^judge/$', views.judge, name='judge'),
    url(r'^result/$', views.result, name='result'),
    url(r'^extract/$', views.extract, name='extract'),
]