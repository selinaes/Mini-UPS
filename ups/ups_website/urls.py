from django.urls import path

from . import views
app_name = 'ups_website'


urlpatterns = [
    path("", views.index, name="index"),
    path('request_shipment/', views.request_tracking, name = 'request_shipment'),

]