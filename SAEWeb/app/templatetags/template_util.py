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
    return type_map[int(value)]

@register.filter(name='decision_value')
def decision_value(str):
    value_map = {
        "M": config.const_IS_TARGET_MULTIPLE,
        "S": config.const_IS_TARGET_SIGNLE,
        "N": config.const_IS_TARGET_NO
    }
    return value_map[str]

@register.filter(name='operation_value')
def operation_value(str):
    value_map = {
        "done": config.socket_CMD_judge_done,
    }
    return value_map[str]