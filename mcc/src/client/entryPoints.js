module.exports = {
  apps: [{
    name: 'dashboard',
    title: 'MCC Data',
    permission: 'read',
    path: './src/client/Dashboard'
  },{
    name: 'dashboardWebpart',
    title: 'MCC Data',
    permission: 'read',
    path: './src/client/Dashboard/webpart',
    generateLib: true
  },{
    name: 'animalRequest',
    title: 'Animal Requests',
    permission: 'read',
    path: './src/client/AnimalRequest'
  },{
    name: 'requestReview',
    title: 'Animal Request Review',
    permission: 'read',
    template: 'home',
    path: './src/client/RequestReview'
  }]
};
