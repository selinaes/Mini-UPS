from django.urls import path

from . import views
app_name = 'ups_website'


urlpatterns = [
    path("", views.index, name="index"),
    
    path('accounts/login/', views.login, name = 'login'),
    path('accounts/logout/', views.logout, name = 'logout'),
    path('accounts/signup/', views.signup, name = 'signup'),
    path('request_shipment/', views.request_tracking, name = 'request_shipment'),
    path('find_shipments/', views.find_all_shipments, name='find_shipments'),
    path('find_packages_detail/<int:shipment_id>/', views.find_packages_detail, name='find_packages_detail'),
    path('change_address/<int:shipment_id>/', views.change_address, name='change_address'),

]