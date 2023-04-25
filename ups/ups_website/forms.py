from django import forms

class TrackingForm(forms.Form):
    shipment_id = forms.CharField(label='shipment_id', max_length=100)

class LoginForm(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password = forms.CharField(label='Password', widget=forms.PasswordInput)

class SignupForm(forms.Form):
    username = forms.CharField(label='Username', max_length=100)
    password = forms.CharField(label='Password', widget=forms.PasswordInput)
    firstname = forms.CharField(label='First name', max_length=100, required=False)
    lastname = forms.CharField(label='Last name', max_length=100, required=False)
    email = forms.EmailField(label='Email Address', required=False)

# class EditUserInfoForm(forms.Form):
#     firstname = forms.CharField(label='First name', max_length=100, required=False)
#     lastname = forms.CharField(label='Last name', max_length=100, required=False)
#     email = forms.EmailField(label='Email Address', required=False)

class EditUserInfoForm(forms.Form):
    address_x = forms.IntegerField(label='Address_x')
    address_y = forms.IntegerField(label='Address_y')