from django import forms

class TrackingForm(forms.Form):
    shipment_id = forms.CharField(label='shipment_id', max_length=100)
