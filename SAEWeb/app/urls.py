__author__ = 'LeoDong'
from django.conf.urls import url
from . import views

urlpatterns = [
    url(r'^$', views.index, name='index'),
    url(r'^judge/$', views.judge, name='judge'),
    url(r'^result/$', views.result, name='result'),
    url(r'^extract/$', views.extract, name='extract'),

    url(r'^rule-panel/$', views.extract_modal_rule, name='rulePanel'),

    url(r'^ajaxExtractor/$',views.ajaxExtractor,name='ajaxExtractor'),

    url(r'^ajaxJudge/$', views.ajaxJudge, name='ajaxJudge'),
    url(r'^file/(?P<type>[0-9A-Za-z\.]+)/(?P<filename>[0-9A-Za-z\.]+)/$', views.loadfile, name='loadfile'),
]