
var recieveflag;

function setflag(flagFromAndroid){
  recieveflag = flagFromAndroid;
  document.getElementById("name").innerHTML = recieveflag;
}

function test(){
  document.getElementById("name").innerHTML = 0;
}
