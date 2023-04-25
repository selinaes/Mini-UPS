from django.shortcuts import render
from django.http import HttpResponse
from django.http import HttpResponseRedirect
from .forms import TrackingForm, LoginForm, SignupForm, EditUserInfoForm
from .models import Truck, Shipments, ProductsInPackage
from django.contrib.auth.decorators import login_required
from django.contrib import auth
from django.contrib.auth import authenticate
from django.contrib.auth.models import User
from django.urls import reverse

@login_required
def index(request):    
    return render(request, 'ups_website/index.html')


@login_required
def index(request):
    user = request.user
    return render(request, 'ups_website/index.html', {'userinfo': user})


@login_required
def logout(request):
    auth.logout(request)
    return HttpResponseRedirect(reverse('ups_website:login'))

def login(request):
    if request.method == 'POST':
        form = LoginForm(request.POST)
        if form.is_valid():
            username = form.cleaned_data['username']
            password = form.cleaned_data['password']
            # check username and password are valid
            user = authenticate(username = username, password = password)
            if user is not None:
                auth.login(request, user)
                return HttpResponseRedirect(reverse('ups_website:index'))
            else:
                error_msg = "User or password is not correct"
                return render(request, 'ups_website/login.html', {'form': form, 'error_msg': error_msg})
        else:
            return render(request, 'ups_website/login.html', {'form': form})
    else:
        form = LoginForm()
    return render(request, 'ups_website/login.html', {'form': form})

@login_required
def signup(request):
    if request.method == 'POST':
        form = SignupForm(request.POST)
        if form.is_valid():
            username = form.cleaned_data.get('username')
            password = form.cleaned_data.get('password')
            firstname = form.cleaned_data.get('firstname')
            lastname = form.cleaned_data.get('lastname')
            email = form.cleaned_data.get('email')
            user = User.objects.create_user(username=username, password=password, first_name=firstname, last_name=lastname, email=email)
            return HttpResponseRedirect(reverse('ups_website:login'))
    else:
        form = SignupForm()
    return render(request, 'ups_website/signup.html', {'form': form})

@login_required
def request_tracking(request):
    if request.method == 'POST':
        form = TrackingForm(request.POST)
        if form.is_valid():
            shipmentID = form.cleaned_data['shipment_id']
            try:
                ship_info = Shipments.objects.get(shipment_id=shipmentID)
            except Shipments.DoesNotExist:
                ship_info = None
            return render(request, 'ups_website/find_shipment.html', {'shipInfo': ship_info})
        else:
            return HttpResponse("Invalid form")
    else:
        form = TrackingForm()
    return render(request, 'ups_website/request_shipment.html', {'form': form})


@login_required
def find_all_shipments(request):
    user = request.user
    shipments = Shipments.objects.filter(ups_username=user.username)
    return render(request, 'ups_website/find_shipments.html', {'shipments_info': shipments})


@login_required
def find_packages_detail(request, shipment_id):
    user = request.user
    shipment = Shipments.objects.get(ups_username=user.username, shipment_id=shipment_id)
    packages = ProductsInPackage.objects.filter(shipment=shipment)
    package = ProductsInPackage.objects.filter(shipment=shipment)
    return render(request, 'ups_website/find_packages_detail.html', {'package_info': packages})

@login_required
def change_address(request, shipment_id):
    user = request.user
    shipment = Shipments.objects.get(ups_username=user.username, shipment_id=shipment_id)
    if request.method == 'POST':
        form = EditUserInfoForm(request.POST)
        if form.is_valid():
            new_address_x = form.cleaned_data['address_x']
            new_address_y = form.cleaned_data['address_y']
            shipment.dest_x = new_address_x
            shipment.dest_y = new_address_y
            shipment.save()
            return HttpResponseRedirect(reverse('ups_website:find_shipments'))
        else:
            return HttpResponse("Invalid form")
    else:
        form = EditUserInfoForm()
    return render(request, 'ups_website/change_address.html', {'form': form})