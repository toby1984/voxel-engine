#version 330

uniform mat4 mvp;
uniform vec3 cameraTranslation;

in vec4 a_position;
in vec2 a_texCoord0;

smooth out vec2 vVaryingTexCoords;

void main(void) {
  vVaryingTexCoords = a_texCoord0;  
  vec4 tmpPos = vec4(a_position.x +  cameraTranslation.x ,a_position.y +  cameraTranslation.y , a_position.z +  cameraTranslation.z , a_position.w); 
  gl_Position = mvp * tmpPos;
}