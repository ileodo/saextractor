__author__ = 'LeoDong'
from django.conf.urls import url
from . import views

urlpatterns = [
    url(r'^$', views.index, name='index'),
    url(r'^judge/$', views.judge, name='judge'),
    url(r'^result/$', views.result, name='result'),
    url(r'^extract/$', views.extract, name='extract'),

    url(r'^extract_model_rules/$', views.extract_modal_rule, name='extract_rules'),
    url(r'^extract_model_preview/$', views.extract_modal_preview, name='extract_preview'),

    url(r'^ajax/$', views.ajax, name='ajax'),
    url(r'^file/(?P<type>[0-9A-Za-z\.]+)/(?P<filename>[0-9A-Za-z\.]+)/$', views.loadfile, name='loadfile'),
]