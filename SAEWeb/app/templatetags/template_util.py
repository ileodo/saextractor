__author__ = 'LeoDong'
from django import template

from util import config

register = template.Library()


@register.filter(name='css_type_for_decision')
def css_type_for_decision(value):
    type_map = {
        config.const_IS_TARGET_MULTIPLE: "-yellow",
        config.const_IS_TARGET_SIGNLE: "-pink",
        config.const_IS_TARGET_NO: "-dark",
        config.const_IS_TARGET_UNKNOW: ""
    }
    return type_map[value]
