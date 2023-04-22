from django.shortcuts import render
from django.http import HttpResponse
from .forms import TrackingForm
from .models import Truck, Shipments, ProductsInPackage

def index(request):    
    return render(request, 'ups_website/index.html')

    
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


# def find_shipment(request):
#     return render(request, 'ups_website/find_shipment.html')