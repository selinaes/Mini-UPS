from django.db import models
from django.contrib.auth.models import User
# from django.contrib.auth.models import User
# from django.contrib.postgres.fields import ArrayField

# # Create your models here.
# # Create your models here.
# class Userinfo(models.Model):
#     user = models.OneToOneField(User, on_delete = models.CASCADE)
#     user_name = models.CharField(max_length=200)
#     driver_status = models.BooleanField()
#     type = models.CharField(max_length=200, null=True)
#     plate = models.CharField(max_length=200, null=True)
#     passengers_num = models.IntegerField(null=True)
#     special_vehicle_info = models.CharField(max_length=200, null=True)

#     def __str__(self):
#         return self.user_name

class Truck(models.Model):
    truck_id = models.IntegerField(primary_key=True)
    truck_x = models.IntegerField(null=True)
    truck_y = models.IntegerField(null=True)
    truck_status = models.CharField(max_length=200, null=True)
    def __str__(self):
        return str(self.truck_id)

class Shipments(models.Model):
    shipment_id = models.IntegerField(primary_key=True)
    ups_username = models.CharField(max_length=200, null=True)
    truck_id = models.IntegerField(null=True)
    wh_id = models.IntegerField(null=True)
    dest_x = models.IntegerField(null=True)
    dest_y = models.IntegerField(null=True)
    shipment_status = models.CharField(max_length=200, null=True)
    def __str__(self):
        return str(self.shipment_id)




class ProductsInPackage(models.Model):
    id = models.AutoField(primary_key=True)
    shipment = models.ForeignKey(Shipments, on_delete=models.CASCADE, null=True)
    product_id = models.IntegerField(null=True)
    product_description = models.CharField(max_length=200, null=True)
    product_quantity = models.IntegerField(null=True)
    def __str__(self):
        return str(self.product_id)