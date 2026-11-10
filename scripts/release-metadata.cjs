const fs=require('node:fs'),crypto=require('node:crypto');
const metadata=JSON.parse(fs.readFileSync('app/build/outputs/apk/release/output-metadata.json','utf8'));
const version=metadata.elements[0],bytes=fs.readFileSync('dist/app.apk');
if(metadata.applicationId!=='app.chains.personal'||!/^\d+\.\d+\.\d+$/.test(version.versionName)||!Number.isInteger(version.versionCode))throw Error('Invalid package metadata');
const badging=fs.readFileSync('dist/package.txt','utf8');
if(!badging.includes("versionCode='"+version.versionCode+"'")||!badging.includes("versionName='"+version.versionName+"'"))throw Error('APK version mismatch');
const manifest={versionCode:version.versionCode,versionName:version.versionName,apkUrl:'https://github.com/rnghx/chains-android/releases/download/v'+version.versionName+'/app.apk',sha256:crypto.createHash('sha256').update(bytes).digest('hex'),size:bytes.length};
fs.writeFileSync('dist/update.json',JSON.stringify(manifest,null,2)+'\n');
console.log('Verified release '+version.versionName+' / '+manifest.size+' bytes');
