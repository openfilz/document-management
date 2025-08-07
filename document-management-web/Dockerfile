# Pull nginx base image
FROM nginx:latest

# Expose port 9981
EXPOSE 9981

# Copy custom configuration file from the current directory
COPY conf/nginx.conf /etc/nginx/nginx.conf

# Copy static assets into var/www
COPY dist/* /var/www/

# Start up nginx server
CMD ["nginx"]