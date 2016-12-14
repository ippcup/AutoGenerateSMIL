# AutoGenerateSMIL
 This Wowza Streaming Engine module gets deployed on all live edge servers (configured as live origins).  It’s responsible for querying the origin server that has the [PushPublishAll](https://github.com/ippcupttocs/PushPublishALL) module running once an onPublish event has occurred on the edge server.  It does an http get to the detected origin server IP grabs the SMIL from the medialist HTTPProvider, then it saves that smil with the expected file name to the default content directory on the edge server.
 
This leverages a more out-of-box way for dynamically creating m3u8 manifests rather than somehow forcing the clients to find the proper origin server and download the SMIL via the same HTTPProvider (excessive load if running the Transcoder on the origin) or having a seperate 3rd party webapp that generates the m3u8.  Getting the SMIL information distributed to all of the edge servers wasn’t an out of box feature, so one had to be made that didn't rely on allowing write access through REST or any other method.

### Prerequisites
Wowza live origin servers with a valid Transcoder licence and a properly configured [PushPublishAll](https://github.com/ippcupttocs/PushPublishALL) module

Edit your conf/VHost.xml file as follows

## HTTPProviders
```
<HTTPProvider>
	<BaseClass>com.wowza.wms.http.HTTPConnectionInfo</BaseClass>
	<RequestFilters>connectioninfo*</RequestFilters>
	<AuthenticationMethod>none</AuthenticationMethod>
</HTTPProvider>
```


Edit your conf/[appname]/Application.xml as follows

## Modules
```
<Module>
	<Name>AutoGenerateSMIL</Name>
	<Description>Automatically create SMIL from pushpublished streams using medialist</Description>
	<Class>org.mycompany.wowza.module.AutoGenerateSMIL</Class>
</Module>
```


## Properties
```
<Property>
	<Name>originAppName</Name>
	<!-- Application name of the origin servers that transcode and push streams to this "edge" Application -->
	<Value>liveorigin</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>originAdminPort</Name>
	<!-- Port on the origin servers where the medialist http provider can be found -->
	<Value>8086</Value>
	<!-- Example http://originIP:8086/medialist?streamname=ngrp:pushedStreamSourceRegex_all&application=liveorigin&format=smil -->
	<Type>Integer</Type>
</Property>
<Property>
	<Name>lastStreamPushed</Name>
	<!-- This should be configured to match the suffix of the last streamname in group of streams pushed by the origin server -->
	<Value>_160p</Value>
	<!-- and not a stream that would be removed by the origin if a threshold has been met by PushPublishALL configured on the origin server -->
	<Type>String</Type>
</Property>
<Property>
	<Name>allStreamsPushed</Name>
	<!-- This should be configured to match the streamnames within the "all" group of streams pushed by the origin server -->
	<Value>_720p|_360p|_240p|_160p</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>pushedStreamSourceRegex</Name>
	<!-- Generic regex matching the streamname defined in the encoder before it's sent to the origin -->
	<Value>^[0-9]++</Value>
	<Type>String</Type>
</Property>
<Property>
	<Name>smilFilenameSuffix</Name>
	<!-- Set this to _all if you want the smil file to match the default stream name group on the origin -->
	<Value>_all</Value>
	<!-- Resulting file will look like "pushedStreamSourceRegex"_all.smil -->
	<Type>String</Type>
</Property>
<Property>
	<Name>securityPublishRequirePassword</Name>
	<Value>true</Value>
	<Type>Boolean</Type>
</Property>
```

## More resources
[Wowza Streaming Engine Server-Side API Reference](https://www.wowza.com/resources/WowzaStreamingEngine_ServerSideAPI.pdf)

[How to extend Wowza Streaming Engine using the Wowza IDE](https://www.wowza.com/forums/content.php?759-How-to-extend-Wowza-Streaming-Engine-using-the-Wowza-IDE)

Wowza Media Systems™ provides developers with a platform to create streaming applications and solutions. See [Wowza Developer Tools](https://www.wowza.com/resources/developers) to learn more about our APIs and SDK.
